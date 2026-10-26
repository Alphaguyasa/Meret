package et.alpha.meret.core.data.tracking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.result.errorOrNull
import et.alpha.meret.core.common.telemetry.NoOpTelemetry
import et.alpha.meret.core.common.time.SystemTimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.datastore.TrackingConfigStore
import et.alpha.meret.core.datastore.TrackingSessionStore
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.TrackingSession
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.Executors

/**
 * The controller against real Room (in memory), a real preferences file and a fake engine.
 *
 * "Process death" is a second controller built on the same database and stores with a fresh
 * engine, which is what a restarted service gets. `runBlocking` on real threads for the same
 * reason as `TrackRecorderTest`: Room's executor is invisible to virtual time.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultTrackingControllerTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var database: MeretDatabase
    private lateinit var thread: ExecutorCoroutineDispatcher
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var sessionStore: TrackingSessionStore
    private lateinit var configStore: TrackingConfigStore

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
        dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { File(temporaryFolder.root, "meret.preferences_pb") },
            )
        sessionStore = TrackingSessionStore(dataStore)
        configStore = TrackingConfigStore(dataStore)
        runBlocking {
            database.trackDao().upsertTrack(track(TRACK_ID))
            // One fix per batch, so every emitted fix is in Room by the time it is counted.
            configStore.set(TrackingConfig(writeBatchSize = 1))
        }
    }

    @After
    fun tearDown() {
        thread.close()
        database.close()
    }

    @Test
    fun `start persists the session, runs the engine and records its fixes`() = runBlocking<Unit> {
        val engine = FakeEngine()
        val controller = controller(engine)

        val result = controller.start(TRACK_ID)

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(controller.state.value)
            .isEqualTo(TrackingState.Recording(TRACK_ID, isPaused = false))
        assertThat(engine.isRunning).isTrue()
        assertThat(sessionStore.session.first()?.trackId).isEqualTo(TRACK_ID)
        assertThat(status()).isEqualTo(TrackStatus.ACTIVE)

        engine.emit(fixes(count = 3))
        awaitPointCount(3)
    }

    @Test
    fun `start on a track with no row fails and opens nothing`() = runBlocking<Unit> {
        val engine = FakeEngine()
        val controller = controller(engine)

        val result = controller.start("no-such-track")

        assertThat(result.errorOrNull()).isInstanceOf(AppError.Storage::class.java)
        assertThat(controller.state.value).isEqualTo(TrackingState.Idle)
        assertThat(engine.isRunning).isFalse()
        assertThat(sessionStore.session.first()).isNull()
    }

    @Test
    fun `start while recording another track is a conflict`() = runBlocking<Unit> {
        database.trackDao().upsertTrack(track(OTHER_TRACK_ID))
        val controller = controller(FakeEngine())
        controller.start(TRACK_ID)

        val again = controller.start(TRACK_ID)
        val other = controller.start(OTHER_TRACK_ID)

        assertThat(again).isEqualTo(AppResult.Success(Unit))
        assertThat(other.errorOrNull()).isInstanceOf(AppError.Conflict::class.java)
        assertThat(sessionStore.session.first()?.trackId).isEqualTo(TRACK_ID)
    }

    @Test
    fun `pause stops the engine and resume restarts it on the same track`() = runBlocking<Unit> {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(TRACK_ID)

        controller.pause()

        assertThat(engine.isRunning).isFalse()
        assertThat(controller.state.value)
            .isEqualTo(TrackingState.Recording(TRACK_ID, isPaused = true))
        assertThat(sessionStore.session.first()?.isPaused).isTrue()
        assertThat(status()).isEqualTo(TrackStatus.PAUSED)

        controller.resume()

        assertThat(engine.isRunning).isTrue()
        assertThat(controller.state.value)
            .isEqualTo(TrackingState.Recording(TRACK_ID, isPaused = false))
        assertThat(sessionStore.session.first()?.isPaused).isFalse()
        assertThat(status()).isEqualTo(TrackStatus.ACTIVE)
    }

    @Test
    fun `stop completes the track, keeps its points and clears the session`() = runBlocking<Unit> {
        val engine = FakeEngine()
        val controller = controller(engine)
        controller.start(TRACK_ID)
        engine.emit(fixes(count = 4))
        awaitPointCount(4)

        controller.stop()

        assertThat(engine.isRunning).isFalse()
        assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(4)
        val track = checkNotNull(database.trackDao().getTrack(TRACK_ID))
        assertThat(track.status).isEqualTo(TrackStatus.COMPLETED)
        assertThat(track.endedAt).isNotNull()
        assertThat(sessionStore.session.first()).isNull()
        assertThat(controller.state.value).isEqualTo(TrackingState.Idle)
    }

    /** Invariant 7: the restarted service continues the same track, not a new one. */
    @Test
    fun `restore after process death continues the same track`() = runBlocking<Unit> {
        val fixes = fixes(count = 5)
        val before = FakeEngine()
        controller(before).start(TRACK_ID)
        before.emit(fixes.take(3))
        awaitPointCount(3)

        val after = FakeEngine()
        val restarted = controller(after)
        val restored = restarted.restore()

        assertThat(restored)
            .isEqualTo(AppResult.Success(TrackingState.Recording(TRACK_ID, isPaused = false)))
        assertThat(after.isRunning).isTrue()
        after.emit(fixes.drop(3))
        awaitPointCount(5)
        val seqs =
            database
                .trackPointDao()
                .getPoints(TRACK_ID)
                .map { it.seq }
                .sorted()
        assertThat(seqs).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `restore of a paused session stays paused with the engine off`() = runBlocking<Unit> {
        sessionStore.start(session().copy(isPaused = true))
        val engine = FakeEngine()
        val controller = controller(engine)

        val restored = controller.restore()

        assertThat(restored)
            .isEqualTo(AppResult.Success(TrackingState.Recording(TRACK_ID, isPaused = true)))
        assertThat(engine.isRunning).isFalse()
    }

    @Test
    fun `restore with no session does nothing`() = runBlocking<Unit> {
        val engine = FakeEngine()

        val restored = controller(engine).restore()

        assertThat(restored).isEqualTo(AppResult.Success(TrackingState.Idle))
        assertThat(engine.isRunning).isFalse()
    }

    @Test
    fun `restore clears a session whose track is already completed`() = runBlocking<Unit> {
        database.trackDao().upsertTrack(track(TRACK_ID).copy(status = TrackStatus.COMPLETED))
        sessionStore.start(session())
        val engine = FakeEngine()

        val restored = controller(engine).restore()

        assertThat(restored).isEqualTo(AppResult.Success(TrackingState.Idle))
        assertThat(engine.isRunning).isFalse()
        assertThat(sessionStore.session.first()).isNull()
    }

    /** A revoked permission must not lose the track: it opens paused, ready to resume. */
    @Test
    fun `a revoked location permission leaves the track open and paused`() = runBlocking<Unit> {
        val engine = FakeEngine(permissionDenied = true)
        val controller = controller(engine)

        val result = controller.start(TRACK_ID)

        assertThat(result.errorOrNull()).isInstanceOf(AppError.Permission::class.java)
        assertThat(controller.state.value)
            .isEqualTo(TrackingState.Recording(TRACK_ID, isPaused = true))
        assertThat(sessionStore.session.first()?.isPaused).isTrue()
        assertThat(status()).isEqualTo(TrackStatus.PAUSED)
    }

    private fun controller(engine: LocationEngine) = DefaultTrackingController(
        engine = engine,
        recorder =
            TrackRecorder(
                trackDao = database.trackDao(),
                trackPointDao = database.trackPointDao(),
                dispatchers = SingleThreadDispatchers(thread),
                timeProvider = SystemTimeProvider(),
                uuidGenerator = UuidV7Generator(SystemTimeProvider()),
            ),
        sessionStore = sessionStore,
        configStore = configStore,
        trackDao = database.trackDao(),
        telemetry = NoOpTelemetry,
    )

    private suspend fun status(): TrackStatus? = database.trackDao().getTrack(TRACK_ID)?.status

    private suspend fun awaitPointCount(expected: Int) {
        withTimeout(TIMEOUT_MS) {
            while (database.trackPointDao().countPoints(TRACK_ID) < expected) delay(POLL_MS)
        }
    }

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

    private fun track(id: String) = TrackEntity(
        id = id,
        projectId = "project-1",
        deviceId = "device-1",
        userId = "user-1",
        startedAt = BASE_MILLIS,
        status = TrackStatus.ACTIVE,
        updatedAt = BASE_MILLIS,
    )

    private fun session() = TrackingSession(
        trackId = TRACK_ID,
        startedAtMillis = BASE_MILLIS,
        startedAtElapsedRealtimeNanos = BASE_NANOS,
    )

    /** Records start/stop and replays fixes on demand. Throws like a real engine on denial. */
    private class FakeEngine(
        private val permissionDenied: Boolean = false,
    ) : LocationEngine {
        private val source = MutableSharedFlow<RawFix>(extraBufferCapacity = 64)

        @Volatile
        var isRunning: Boolean = false
            private set

        override val fixes: Flow<RawFix> = source

        override val capabilities: EngineCapabilities =
            EngineCapabilities(
                kind = EngineKind.REPLAY,
                supportsBatching = false,
                supportsGnssStatus = false,
                reportsSpeed = false,
            )

        override suspend fun start(config: TrackingConfig) {
            if (permissionDenied) throw SecurityException("location permission revoked")
            isRunning = true
        }

        override suspend fun stop() {
            isRunning = false
        }

        override suspend fun lastKnown(): RawFix? = null

        suspend fun emit(fixes: List<RawFix>) {
            fixes.forEach { source.emit(it) }
        }
    }

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
        const val OTHER_TRACK_ID = "track-2"

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
