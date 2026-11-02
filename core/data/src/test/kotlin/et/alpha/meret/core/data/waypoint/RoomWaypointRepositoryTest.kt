package et.alpha.meret.core.data.waypoint

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.common.uuid.uuidV7TimestampMillis
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.WaypointMarker
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.WaypointCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Against real Room, in memory, as `RoomNewTrackStoreTest`. */
@RunWith(RobolectricTestRunner::class)
class RoomWaypointRepositoryTest {
    private lateinit var database: MeretDatabase
    private lateinit var repository: RoomWaypointRepository

    private val waypoint =
        NewWaypoint(
            position = LatLng(9.0301, 38.7402),
            accuracyMetres = 4.5,
            altitudeMetres = 2_355.0,
            trackId = null,
            category = WaypointCategory.WATER_POINT,
            title = "Borehole",
            notes = "Pump broken",
        )

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
            RoomWaypointRepository(
                waypointDao = database.waypointDao(),
                ids = UuidV7Generator(FixedTime),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a saved waypoint is a pending row with everything the worker entered`() =
        runBlocking<Unit> {
            val id = (repository.save(waypoint) as AppResult.Success).value

            val row = checkNotNull(database.waypointDao().getWaypoint(id))
            assertThat(row.latitude).isEqualTo(9.0301)
            assertThat(row.longitude).isEqualTo(38.7402)
            assertThat(row.accuracyMeters).isEqualTo(4.5)
            assertThat(row.altitudeMeters).isEqualTo(2_355.0)
            assertThat(row.trackId).isNull()
            assertThat(row.category).isEqualTo("water_point")
            assertThat(row.title).isEqualTo("Borehole")
            assertThat(row.notes).isEqualTo("Pump broken")
            assertThat(row.recordedAt).isEqualTo(FixedTime.nowMillis())
            assertThat(row.updatedAt).isEqualTo(FixedTime.nowMillis())
            assertThat(row.syncState).isEqualTo(SyncState.PENDING)
            assertThat(row.baseVersion).isEqualTo(0)
            assertThat(row.projectId).isEqualTo(RoomNewTrackStore.LOCAL_PROJECT_ID)
            assertThat(uuidV7TimestampMillis(id)).isEqualTo(FixedTime.nowMillis())
        }

    @Test
    fun `a waypoint saved while recording keeps its track, with no track row needed`() =
        runBlocking<Unit> {
            val saved = repository.save(waypoint.copy(trackId = "track-7"))
            val id = (saved as AppResult.Success).value

            assertThat(database.waypointDao().getWaypoint(id)?.trackId).isEqualTo("track-7")
        }

    @Test
    fun `every save is a new row`() = runBlocking<Unit> {
        val first = (repository.save(waypoint) as AppResult.Success).value
        val second = (repository.save(waypoint) as AppResult.Success).value

        assertThat(first).isNotEqualTo(second)
        assertThat(database.waypointDao().getWaypointsBySyncState(SyncState.PENDING)).hasSize(2)
    }

    @Test
    fun `a waypoint with no category, title or notes is saved with none`() = runBlocking<Unit> {
        val bare = waypoint.copy(category = null, title = null, notes = null, altitudeMetres = null)

        val id = (repository.save(bare) as AppResult.Success).value

        val row = checkNotNull(database.waypointDao().getWaypoint(id))
        assertThat(row.category).isNull()
        assertThat(row.title).isNull()
        assertThat(row.notes).isNull()
        assertThat(row.altitudeMeters).isNull()
    }

    @Test
    fun `a saved waypoint appears among the map's markers, newest first`() = runBlocking<Unit> {
        val first = (repository.save(waypoint) as AppResult.Success).value
        val second =
            (
                repository.save(
                    waypoint.copy(category = null, position = LatLng(9.1, 38.8)),
                ) as AppResult.Success
            ).value

        val markers = repository.observeMarkers().first()

        assertThat(markers)
            .containsExactly(
                WaypointMarker(second, LatLng(9.1, 38.8), null),
                WaypointMarker(first, LatLng(9.0301, 38.7402), "water_point"),
            ).inOrder()
    }

    @Test
    fun `the list holds every saved waypoint, newest first, with what the worker entered`() =
        runBlocking<Unit> {
            val first = (repository.save(waypoint) as AppResult.Success).value
            val second =
                (repository.save(waypoint.copy(title = null)) as AppResult.Success).value

            val list = repository.observeWaypoints().first()

            assertThat(list.map { it.id }).containsExactly(second, first).inOrder()
            assertThat(list[0].title).isNull()
            assertThat(list[1].title).isEqualTo("Borehole")
            assertThat(list[1].category).isEqualTo("water_point")
            assertThat(list[1].position).isEqualTo(LatLng(9.0301, 38.7402))
            assertThat(list[1].syncState).isEqualTo(SyncState.PENDING)
        }

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = 1_761_000_000_000L

        override fun elapsedRealtimeNanos(): Long = 0L
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }
}
