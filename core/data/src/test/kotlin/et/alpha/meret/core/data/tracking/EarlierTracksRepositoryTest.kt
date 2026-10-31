package et.alpha.meret.core.data.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackPointEntity
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Against real Room (in memory): the area query is SQL, and only worth testing as SQL. */
@RunWith(RobolectricTestRunner::class)
class EarlierTracksRepositoryTest {
    private lateinit var database: MeretDatabase
    private lateinit var repository: DefaultEarlierTracksRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MeretDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository =
            DefaultEarlierTracksRepository(
                trackDao = database.trackDao(),
                trackPointDao = database.trackPointDao(),
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `only finished tracks that meet the area are drawn, newest first`() = runBlocking<Unit> {
        record("older", startedAt = 1_000L, line = ADDIS_WALK)
        record("newer", startedAt = 2_000L, line = ADDIS_WALK.shifted(0.01))
        record("mekelle", startedAt = 3_000L, line = ADDIS_WALK.shifted(4.5))
        record("recording", startedAt = 4_000L, line = ADDIS_WALK, status = TrackStatus.ACTIVE)
        record("paused", startedAt = 5_000L, line = ADDIS_WALK, status = TrackStatus.PAUSED)
        record("deleted", startedAt = 6_000L, line = ADDIS_WALK, status = TrackStatus.DISCARDED)
        record("no fix", startedAt = 7_000L, line = emptyList())

        val lines = repository.observeEarlierTracks(ADDIS).first()

        assertThat(lines)
            .containsExactly(
                TrackLine(listOf(ADDIS_WALK.shifted(0.01))),
                TrackLine(listOf(ADDIS_WALK)),
            ).inOrder()
    }

    @Test
    fun `a track that only crosses the edge of the area is drawn`() = runBlocking<Unit> {
        val crossing = listOf(LatLng(9.05, 38.75), LatLng(9.25, 38.75))
        record("crossing", startedAt = 1_000L, line = crossing)

        val lines = repository.observeEarlierTracks(ADDIS).first()

        assertThat(lines).containsExactly(TrackLine(listOf(crossing)))
    }

    @Test
    fun `outliers are left out of the line and a pause leaves a gap`() = runBlocking<Unit> {
        val first = listOf(LatLng(9.00, 38.75), LatLng(9.01, 38.75))
        val second = listOf(LatLng(9.02, 38.76), LatLng(9.03, 38.76))
        database.trackDao().upsertTrack(track("walk", startedAt = 1_000L))
        database.trackPointDao().insertPoints(
            listOf(
                point("walk", segment = 0, seq = 0, at = first[0]),
                point("walk", segment = 0, seq = 1, at = first[1]),
                // 40 km off: rejected by the filter, kept in Room, never drawn.
                point("walk", segment = 0, seq = 2, at = LatLng(9.4, 38.75), isOutlier = true),
                point("walk", segment = 1, seq = 3, at = second[0]),
                point("walk", segment = 1, seq = 4, at = second[1]),
            ),
        )
        database.trackPointDao().recomputeBounds("walk")

        val lines = repository.observeEarlierTracks(ADDIS).first()

        assertThat(lines).containsExactly(TrackLine(listOf(first, second)))
        // The raw fixes are untouched.
        assertThat(database.trackPointDao().countPoints("walk")).isEqualTo(5)
    }

    @Test
    fun `at most the newest thirty tracks are drawn`() = runBlocking<Unit> {
        repeat(EarlierTracksRepository.MAX_TRACKS + 5) { index ->
            record("track-$index", startedAt = 1_000L + index, line = ADDIS_WALK)
        }

        val lines = repository.observeEarlierTracks(ADDIS).first()

        assertThat(lines).hasSize(EarlierTracksRepository.MAX_TRACKS)
    }

    @Test
    fun `a track that changes is drawn as it now is`() = runBlocking<Unit> {
        record("walk", startedAt = 1_000L, line = ADDIS_WALK)
        assertThat(repository.observeEarlierTracks(ADDIS).first())
            .containsExactly(TrackLine(listOf(ADDIS_WALK)))

        // Reopened, walked on in a new segment, stopped again: new points, new updated_at.
        val more = listOf(LatLng(9.03, 38.77), LatLng(9.04, 38.77))
        database.trackPointDao().insertPoints(
            more.mapIndexed { index, at -> point("walk", segment = 1, seq = 10 + index, at = at) },
        )
        database.trackDao().upsertTrack(
            track("walk", startedAt = 1_000L).copy(
                pointCount = ADDIS_WALK.size + more.size,
                updatedAt = 9_000L,
            ),
        )
        database.trackPointDao().recomputeBounds("walk")

        assertThat(repository.observeEarlierTracks(ADDIS).first())
            .containsExactly(TrackLine(listOf(ADDIS_WALK, more)))
    }

    @Test
    fun `stopping a recording brings it onto the map`() = runBlocking<Unit> {
        record("walk", startedAt = 1_000L, line = ADDIS_WALK, status = TrackStatus.ACTIVE)
        assertThat(repository.observeEarlierTracks(ADDIS).first()).isEmpty()

        database.trackDao().endTrack(
            "walk",
            TrackStatus.COMPLETED,
            endedAt = 2_000L,
            updatedAt = 2_000L,
        )

        assertThat(repository.observeEarlierTracks(ADDIS).first()).hasSize(1)
    }

    /** A one-segment track along [line], with bounds as `appendBatch` would keep them. */
    private suspend fun record(
        id: String,
        startedAt: Long,
        line: List<LatLng>,
        status: TrackStatus = TrackStatus.COMPLETED,
    ) {
        database.trackDao().upsertTrack(
            track(id, startedAt, status).copy(pointCount = line.size),
        )
        database.trackPointDao().insertPoints(
            line.mapIndexed { index, at -> point(id, segment = 0, seq = index, at = at) },
        )
        database.trackPointDao().recomputeBounds(id)
    }

    private fun track(
        id: String,
        startedAt: Long,
        status: TrackStatus = TrackStatus.COMPLETED,
    ) = TrackEntity(
        id = id,
        projectId = "local",
        deviceId = "device",
        userId = "local",
        startedAt = startedAt,
        status = status,
        updatedAt = startedAt,
    )

    private fun point(
        trackId: String,
        segment: Int,
        seq: Int,
        at: LatLng,
        isOutlier: Boolean = false,
    ) = TrackPointEntity(
        id = "$trackId-$seq",
        trackId = trackId,
        segmentIndex = segment,
        seq = seq,
        latitude = at.latitude,
        longitude = at.longitude,
        accuracyMeters = 4.0,
        provider = LocationProvider.GPS,
        recordedAt = seq * 1_000L,
        elapsedRealtimeNanos = seq * 1_000_000_000L,
        isOutlier = isOutlier,
    )

    private fun List<LatLng>.shifted(latitude: Double) =
        map { LatLng(it.latitude + latitude, it.longitude) }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private companion object {
        /** Central Addis Ababa. */
        val ADDIS = BoundingBox(8.95, 38.70, 9.10, 38.85)

        /** A straight walk north, two points: nothing for Douglas–Peucker to drop. */
        val ADDIS_WALK = listOf(LatLng(9.00, 38.75), LatLng(9.02, 38.75))
    }
}
