package et.alpha.meret.core.data.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Against real Room (in memory), as in `RoomNewTrackStoreTest`. */
@RunWith(RobolectricTestRunner::class)
class TrackRepositoryTest {
    private lateinit var database: MeretDatabase
    private val tracking = FakeTracking()
    private lateinit var repository: DefaultTrackRepository

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
            DefaultTrackRepository(
                trackDao = database.trackDao(),
                tracking = tracking,
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the list is newest first and maps every field the list shows`() = runBlocking<Unit> {
        database.trackDao().upsertTracks(
            listOf(
                track("older", startedAt = 1_000L),
                track("newer", startedAt = 2_000L).copy(
                    name = "Kebele 03",
                    distanceMeters = 1_234.5,
                    movingDurationMillis = 600_000L,
                    syncState = SyncState.SYNCED,
                ),
            ),
        )

        val tracks = repository.observeTracks().first()

        assertThat(tracks.map(Track::id)).containsExactly("newer", "older").inOrder()
        val newer = tracks.first()
        assertThat(newer.name).isEqualTo("Kebele 03")
        assertThat(newer.distanceMeters).isEqualTo(1_234.5)
        assertThat(newer.movingDurationMillis).isEqualTo(600_000L)
        assertThat(newer.syncState).isEqualTo(SyncState.SYNCED)
    }

    @Test
    fun `a discarded track leaves the list but keeps its row`() = runBlocking<Unit> {
        database.trackDao().upsertTrack(track("done"))

        val result = repository.discardTrack("done")

        assertThat(result).isEqualTo(AppResult.Success(Unit))
        assertThat(repository.observeTracks().first()).isEmpty()
        val row = checkNotNull(database.trackDao().getTrack("done"))
        assertThat(row.status).isEqualTo(TrackStatus.DISCARDED)
        assertThat(row.updatedAt).isEqualTo(FixedTime.nowMillis())
    }

    @Test
    fun `the track being recorded cannot be discarded`() = runBlocking<Unit> {
        database.trackDao().upsertTrack(track("live", status = TrackStatus.ACTIVE))
        tracking.state.value = TrackingState.Recording("live", isPaused = true)

        val result = repository.discardTrack("live")

        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Conflict::class.java)
        assertThat(checkNotNull(database.trackDao().getTrack("live")).status)
            .isEqualTo(TrackStatus.ACTIVE)
    }

    @Test
    fun `an open track nobody is recording can be discarded`() = runBlocking<Unit> {
        database.trackDao().upsertTrack(track("left-open", status = TrackStatus.ACTIVE))
        tracking.state.value = TrackingState.Recording("another", isPaused = false)

        assertThat(repository.discardTrack("left-open")).isEqualTo(AppResult.Success(Unit))
    }

    @Test
    fun `discarding a track that is not there is not found`() = runBlocking<Unit> {
        val error = (repository.discardTrack("missing") as AppResult.Failure).error

        assertThat((error as AppError.Storage).kind).isEqualTo(AppError.Storage.Kind.NotFound)
    }

    private fun track(
        id: String,
        startedAt: Long = 1_000L,
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

    private class FakeTracking : TrackingController {
        override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)
        override val activeTrack: Flow<Track?> = emptyFlow()
        override val activeLine: Flow<TrackLine?> = emptyFlow()
        override val health = MutableStateFlow(TrackingHealth())

        override suspend fun start(trackId: String): AppResult<Unit> = error("not used")

        override suspend fun restore(): AppResult<TrackingState> = error("not used")

        override suspend fun interruptedTrackId(): String? = null

        override suspend fun pause() = Unit

        override suspend fun resume(): AppResult<Unit> = error("not used")

        override suspend fun stop() = Unit
    }
}
