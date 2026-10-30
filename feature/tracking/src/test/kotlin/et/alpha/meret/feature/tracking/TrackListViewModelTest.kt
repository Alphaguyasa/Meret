package et.alpha.meret.feature.tracking

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackRepository
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackDetail
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.feature.tracking.TrackListViewModel.Companion.UNDO_WINDOW_MS
import et.alpha.meret.feature.tracking.service.FakeTrackingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()
    private val tracking = FakeTrackingController()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tracks are listed in the repository's order with their stats`() = runTest(dispatcher) {
        repository.tracks.value =
            listOf(
                track("newer", startedAt = 2_000L, syncState = SyncState.SYNCED),
                track("older", startedAt = 1_000L),
            )

        val state = subscribe(TrackListViewModel(repository, tracking))

        assertThat(state().tracks)
            .containsExactly(
                item("newer", startedAt = 2_000L, syncState = SyncState.SYNCED),
                item("older", startedAt = 1_000L),
            ).inOrder()
        assertThat(state().pendingDelete).isNull()
    }

    @Test
    fun `the track being recorded shows as recording or paused and cannot be deleted`() =
        runTest(dispatcher) {
            repository.tracks.value = listOf(track("live", status = TrackStatus.ACTIVE))
            tracking.state.value = TrackingState.Recording("live", isPaused = false)
            val state = subscribe(TrackListViewModel(repository, tracking))

            assertThat(state().tracks.single().progress).isEqualTo(TrackProgress.Recording)
            assertThat(state().tracks.single().canDelete).isFalse()

            tracking.state.value = TrackingState.Recording("live", isPaused = true)

            assertThat(state().tracks.single().progress).isEqualTo(TrackProgress.Paused)
        }

    @Test
    fun `an open track nobody is recording shows as not finished, never as recording`() =
        runTest(dispatcher) {
            repository.tracks.value =
                listOf(
                    track("left-open", status = TrackStatus.ACTIVE),
                    track("left-paused", status = TrackStatus.PAUSED),
                    track("live", status = TrackStatus.ACTIVE),
                )
            tracking.state.value = TrackingState.Recording("live", isPaused = false)

            val state = subscribe(TrackListViewModel(repository, tracking))

            assertThat(state().tracks.map { it.progress })
                .containsExactly(
                    TrackProgress.NotFinished,
                    TrackProgress.NotFinished,
                    TrackProgress.Recording,
                ).inOrder()
            assertThat(state().tracks.first().canDelete).isTrue()
        }

    @Test
    fun `a delete hides the row at once and writes nothing until the window closes`() =
        runTest(dispatcher) {
            repository.tracks.value = listOf(track("a"), track("b"))
            val viewModel = TrackListViewModel(repository, tracking)
            val state = subscribe(viewModel)

            viewModel.deleteTrack("a")

            assertThat(state().tracks.map { it.id }).containsExactly("b")
            assertThat(state().pendingDelete?.id).isEqualTo("a")
            advanceTimeBy(UNDO_WINDOW_MS - 1)
            assertThat(repository.discarded).isEmpty()

            advanceTimeBy(2)

            assertThat(repository.discarded).containsExactly("a")
            assertThat(state().pendingDelete).isNull()
            assertThat(state().tracks.map { it.id }).containsExactly("b")
        }

    @Test
    fun `undo inside the window brings the row back and nothing is written`() =
        runTest(dispatcher) {
            repository.tracks.value = listOf(track("a"))
            val viewModel = TrackListViewModel(repository, tracking)
            val state = subscribe(viewModel)

            viewModel.deleteTrack("a")
            advanceTimeBy(UNDO_WINDOW_MS / 2)
            viewModel.undoDelete()
            advanceTimeBy(UNDO_WINDOW_MS * 2)

            assertThat(repository.discarded).isEmpty()
            assertThat(state().tracks.map { it.id }).containsExactly("a")
            assertThat(state().pendingDelete).isNull()
        }

    @Test
    fun `the app dying inside the window keeps the track`() = runTest(dispatcher) {
        repository.tracks.value = listOf(track("a"))
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { TrackListViewModel(repository, tracking) } }
        val viewModel = ViewModelProvider(store, factory)[TrackListViewModel::class.java]
        subscribe(viewModel)

        viewModel.deleteTrack("a")
        // Leaving the app clears the view model; process death never runs the timer at all.
        store.clear()
        advanceTimeBy(UNDO_WINDOW_MS * 2)

        assertThat(repository.discarded).isEmpty()
    }

    @Test
    fun `a second delete inside the window commits the first one now`() = runTest(dispatcher) {
        repository.tracks.value = listOf(track("a"), track("b"), track("c"))
        val viewModel = TrackListViewModel(repository, tracking)
        val state = subscribe(viewModel)

        viewModel.deleteTrack("a")
        viewModel.deleteTrack("b")

        assertThat(repository.discarded).containsExactly("a")
        assertThat(state().pendingDelete?.id).isEqualTo("b")
        assertThat(state().tracks.map { it.id }).containsExactly("c")

        viewModel.undoDelete()

        assertThat(state().tracks.map { it.id }).containsExactly("b", "c").inOrder()
        advanceTimeBy(UNDO_WINDOW_MS * 2)
        assertThat(repository.discarded).containsExactly("a")
    }

    @Test
    fun `a refused delete brings the row back and says why`() = runTest(dispatcher) {
        repository.tracks.value = listOf(track("a"))
        repository.result = AppResult.Failure(AppError.Conflict("track", "a"))
        val viewModel = TrackListViewModel(repository, tracking)
        val state = subscribe(viewModel)

        viewModel.deleteTrack("a")
        advanceTimeBy(UNDO_WINDOW_MS + 1)

        assertThat(state().tracks.map { it.id }).containsExactly("a")
        assertThat(state().message).isEqualTo(TrackListMessage.DeleteRefusedRecording)

        viewModel.dismissMessage()

        assertThat(state().message).isNull()
    }

    @Test
    fun `a failed write brings the row back with a message`() = runTest(dispatcher) {
        repository.tracks.value = listOf(track("a"))
        repository.result =
            AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
        val viewModel = TrackListViewModel(repository, tracking)
        val state = subscribe(viewModel)

        viewModel.deleteTrack("a")
        advanceTimeBy(UNDO_WINDOW_MS + 1)

        assertThat(state().tracks.map { it.id }).containsExactly("a")
        assertThat(state().message).isEqualTo(TrackListMessage.DeleteFailed)
    }

    /** Keeps the state flow subscribed for the test, and reads its current Ready value. */
    private fun TestScope.subscribe(viewModel: TrackListViewModel): () -> TrackListUiState.Ready {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return { viewModel.uiState.value as TrackListUiState.Ready }
    }

    private fun track(
        id: String,
        startedAt: Long = 1_000L,
        status: TrackStatus = TrackStatus.COMPLETED,
        syncState: SyncState = SyncState.PENDING,
    ) = Track(
        id = id,
        projectId = "local",
        deviceId = "device",
        userId = "local",
        startedAt = startedAt,
        status = status,
        distanceMeters = DISTANCE,
        movingDurationMillis = MOVING_MS,
        syncState = syncState,
        updatedAt = startedAt,
    )

    private fun item(
        id: String,
        startedAt: Long,
        syncState: SyncState = SyncState.PENDING,
    ) = TrackItem(
        id = id,
        name = null,
        startedAtMillis = startedAt,
        distanceMetres = DISTANCE,
        movingDurationMillis = MOVING_MS,
        progress = TrackProgress.Finished,
        syncState = syncState,
    )

    /** Discards the way Room does: the track leaves the list. */
    private class FakeRepository : TrackRepository {
        val tracks = MutableStateFlow<List<Track>>(emptyList())
        val discarded = mutableListOf<String>()
        var result: AppResult<Unit> = AppResult.Success(Unit)

        override fun observeTracks(): Flow<List<Track>> = tracks

        override fun observeTrackDetail(trackId: String): Flow<TrackDetail?> = flowOf(null)

        override suspend fun discardTrack(trackId: String): AppResult<Unit> {
            val outcome = result
            if (outcome is AppResult.Success) {
                discarded += trackId
                tracks.value = tracks.value.filterNot { it.id == trackId }
            }
            return outcome
        }
    }

    private companion object {
        const val DISTANCE = 1_250.0
        const val MOVING_MS = 900_000L
    }
}
