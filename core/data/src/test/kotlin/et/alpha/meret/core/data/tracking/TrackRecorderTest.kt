package et.alpha.meret.core.data.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.time.SystemTimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackPointEntity
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.location.engine.GpxSource
import et.alpha.meret.core.location.engine.ReplayLocationEngine
import et.alpha.meret.core.location.engine.ReplaySpeed
import et.alpha.meret.core.location.filter.FixPipeline
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/**
 * The recorder against real Room (in memory) and real GPX replays.
 *
 * `runBlocking` and real threads rather than `runTest`: Room's suspend DAOs run on Room's own
 * executor, which virtual time cannot see, and a `withTimeout` under `runTest` would expire
 * the instant the test thread went idle waiting for it. Everything of ours — engine, tap,
 * recorder — shares one thread, so the replay engine's `yield` between fixes hands the
 * collector its turn and nothing overflows the engine's buffer.
 */
@RunWith(RobolectricTestRunner::class)
class TrackRecorderTest {
    private lateinit var database: MeretDatabase
    private lateinit var thread: ExecutorCoroutineDispatcher
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MeretDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + thread)
        runBlocking { database.trackDao().upsertTrack(track()) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        thread.close()
        database.close()
    }

    @Test
    fun `every replayed fix is in Room after stop, with the raw columns unchanged`() =
        runBlocking<Unit> {
            val emitted = replay(CLEAN_WALK)

            val rows = rows()
            assertThat(rows).hasSize(emitted.size)
            assertThat(rows.map { it.seq }).isEqualTo(emitted.indices.toList())
            rows.zip(emitted).forEach { (row, fix) ->
                assertThat(row.latitude).isEqualTo(fix.position.latitude)
                assertThat(row.longitude).isEqualTo(fix.position.longitude)
                assertThat(row.accuracyMeters).isEqualTo(fix.accuracyMetres)
                assertThat(row.altitudeMeters).isEqualTo(fix.altitudeMetres)
                assertThat(row.elapsedRealtimeNanos).isEqualTo(fix.elapsedRealtimeNanos)
                assertThat(row.recordedAt).isEqualTo(fix.recordedAtMillis)
                assertThat(row.provider).isEqualTo(fix.provider)
            }
        }

    /**
     * The fixture's six injected 200 m jumps (indices from `core/location`'s `Traces`) are
     * stored and flagged, not dropped, and nothing else is flagged.
     */
    @Test
    fun `outliers are stored and flagged`() = runBlocking<Unit> {
        val emitted = replay(WALK_WITH_OUTLIERS)

        val rows = rows()
        assertThat(rows).hasSize(emitted.size)
        assertThat(rows.filter { it.isOutlier }.map { it.seq })
            .containsExactly(97, 198, 301, 404, 512, 631)
            .inOrder()
        assertThat(rows.filter { it.isOutlier }.all { it.filteredLatitude == null }).isTrue()
    }

    /**
     * The batched deltas must add up to exactly what one pipeline over the whole trace
     * reports. A delta taken against the wrong baseline drifts by a batch's worth per batch.
     */
    @Test
    fun `track totals equal the pipeline's totals`() = runBlocking<Unit> {
        val emitted = replay(CLEAN_WALK)

        val pipeline =
            FixPipeline(TRACK_ID, TrackingConfig(), UuidV7Generator(SystemTimeProvider()))
        emitted.forEach { pipeline.process(it) }

        val track = checkNotNull(database.trackDao().getTrack(TRACK_ID))
        assertThat(track.pointCount).isEqualTo(emitted.size)
        assertThat(track.distanceMeters).isWithin(1e-6).of(pipeline.totalDistanceMetres)
        assertThat(track.movingDurationMillis).isEqualTo(pipeline.totalMovingDurationMillis)
    }

    /**
     * A batch larger than the whole trace and a window that never elapses: nothing reaches Room
     * until stop, and then everything does. The default batch of 20 over the 715-fix fixture
     * leaves a 15-fix tail, covered by the test above.
     */
    @Test
    fun `stop writes the partial batch`() = runBlocking<Unit> {
        val config = TrackingConfig(writeBatchSize = 10_000, writeBatchWindowMillis = NEVER)
        val engine = engine(CLEAN_WALK)
        val recording = recorder().start(scope, TRACK_ID, config, engine.fixes)
        engine.start(config)
        val total = fixtureSize(CLEAN_WALK)
        withTimeout(TIMEOUT_MS) { recording.fixesReceived.first { it == total } }

        assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(0)

        engine.stop()
        recording.stop()

        assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(total)
        assertThat(engine.droppedFixCount.value).isEqualTo(0)
    }

    @Test
    fun `a full batch is written without waiting for the window`() = runBlocking<Unit> {
        val source = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)
        val config = TrackingConfig(writeBatchSize = 3, writeBatchWindowMillis = NEVER)
        recorder().start(scope, TRACK_ID, config, source)

        fixes(count = 4).forEach { source.emit(it) }

        awaitPointCount(3)
        assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(3)
    }

    @Test
    fun `the time window writes a batch that never fills`() = runBlocking<Unit> {
        val source = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)
        val config = TrackingConfig(writeBatchSize = 10_000, writeBatchWindowMillis = 50L)
        recorder().start(scope, TRACK_ID, config, source)

        fixes(count = 3).forEach { source.emit(it) }

        awaitPointCount(3)
    }

    @Test
    fun `pause writes immediately, records nothing while paused, and resume opens a segment`() =
        runBlocking<Unit> {
            val source = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)
            val config = TrackingConfig(writeBatchSize = 10_000, writeBatchWindowMillis = NEVER)
            val recording = recorder().start(scope, TRACK_ID, config, source)
            val fixes = fixes(count = 9)

            fixes.take(3).forEach { source.emit(it) }
            withTimeout(TIMEOUT_MS) { recording.fixesReceived.first { it == 3 } }
            recording.pause()
            assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(3)

            fixes.subList(3, 6).forEach { source.emit(it) }
            withTimeout(TIMEOUT_MS) { recording.fixesReceived.first { it == 6 } }
            recording.resume()
            fixes.subList(6, 9).forEach { source.emit(it) }
            withTimeout(TIMEOUT_MS) { recording.fixesReceived.first { it == 9 } }
            recording.stop()

            val rows = rows()
            assertThat(rows.map { it.seq }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
            assertThat(rows.map { it.elapsedRealtimeNanos })
                .isEqualTo((fixes.take(3) + fixes.subList(6, 9)).map { it.elapsedRealtimeNanos })
            assertThat(rows.last().segmentIndex).isGreaterThan(rows.first().segmentIndex)
        }

    /**
     * Process death: a new recorder on a track that already has rows must continue the `seq`
     * numbering. Restarting at 0 would collide on `(track_id, seq)` and IGNORE would discard
     * the whole batch without a word.
     */
    @Test
    fun `a new recording on an existing track continues it`() = runBlocking<Unit> {
        val config = TrackingConfig(writeBatchSize = 10_000, writeBatchWindowMillis = NEVER)
        val fixes = fixes(count = 5)

        val first = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)
        val firstRecording = recorder().start(scope, TRACK_ID, config, first)
        fixes.take(3).forEach { first.emit(it) }
        withTimeout(TIMEOUT_MS) { firstRecording.fixesReceived.first { it == 3 } }
        firstRecording.stop()

        val second = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)
        val secondRecording = recorder().start(scope, TRACK_ID, config, second)
        fixes.drop(3).forEach { second.emit(it) }
        withTimeout(TIMEOUT_MS) { secondRecording.fixesReceived.first { it == 2 } }
        secondRecording.stop()

        assertThat(rows().map { it.seq }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(checkNotNull(database.trackDao().getTrack(TRACK_ID)).pointCount).isEqualTo(5)
    }

    /** Replays a whole fixture through a recording and stops it. Returns what the engine emitted. */
    private suspend fun replay(trace: String): List<RawFix> {
        val config = TrackingConfig()
        val engine = engine(trace)
        val emitted = Collections.synchronizedList(mutableListOf<RawFix>())
        val tap =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                engine.fixes.collect { emitted += it }
            }
        val recording = recorder().start(scope, TRACK_ID, config, engine.fixes)

        engine.start(config)
        val total = fixtureSize(trace)
        withTimeout(TIMEOUT_MS) { recording.fixesReceived.first { it == total } }
        engine.stop()
        recording.stop()
        tap.cancel()

        assertThat(engine.droppedFixCount.value).isEqualTo(0)
        return emitted.toList()
    }

    private fun engine(trace: String) = ReplayLocationEngine(
        scope = scope,
        source = GpxSource { File(TRACES, trace).inputStream() },
        speed = ReplaySpeed.INSTANT,
    )

    private fun recorder() = TrackRecorder(
        trackDao = database.trackDao(),
        trackPointDao = database.trackPointDao(),
        dispatchers = SingleThreadDispatchers(thread),
        timeProvider = SystemTimeProvider(),
        uuidGenerator = UuidV7Generator(SystemTimeProvider()),
    )

    private suspend fun rows(): List<TrackPointEntity> =
        database.trackPointDao().getPoints(TRACK_ID).sortedBy { it.seq }

    private suspend fun awaitPointCount(expected: Int) {
        withTimeout(TIMEOUT_MS) {
            while (database.trackPointDao().countPoints(TRACK_ID) < expected) delay(POLL_MS)
        }
    }

    /** Counted from the file, so the test does not hard-code a number the fixture owns. */
    private fun fixtureSize(trace: String): Int =
        Regex("<trkpt\\b").findAll(File(TRACES, trace).readText()).count()

    /** A slow, accurate walk north: one fix a second, two metres apart. */
    private fun fixes(count: Int): List<RawFix> = List(count) { index ->
        RawFix(
            position = LatLng(BASE_LATITUDE + index * METRES_TO_DEGREES * 2, BASE_LONGITUDE),
            accuracyMetres = 5.0,
            elapsedRealtimeNanos = BASE_NANOS + index * NANOS_PER_SECOND,
            recordedAtMillis = BASE_MILLIS + index * MILLIS_PER_SECOND,
            provider = LocationProvider.GPS,
        )
    }

    private fun track() = TrackEntity(
        id = TRACK_ID,
        projectId = "project-1",
        deviceId = "device-1",
        userId = "user-1",
        startedAt = BASE_MILLIS,
        status = TrackStatus.ACTIVE,
        updatedAt = BASE_MILLIS,
    )

    private class SingleThreadDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private companion object {
        const val TRACK_ID = "track-1"

        // Shared with `core/location`'s tests rather than copied: the module's working
        // directory is its own root when Gradle runs unit tests.
        val TRACES = File("../location/src/test/resources/traces")
        const val CLEAN_WALK = "clean_walk_1km.gpx"
        const val WALK_WITH_OUTLIERS = "walk_1km_with_outliers.gpx"

        const val NEVER = 3_600_000L
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 10L

        const val BASE_LATITUDE = 9.0300
        const val BASE_LONGITUDE = 38.7400
        const val METRES_TO_DEGREES = 1.0 / 111_320.0
        const val BASE_NANOS = 3_600_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val BASE_MILLIS = 1_767_225_600_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
