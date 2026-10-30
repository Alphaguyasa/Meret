package et.alpha.meret.feature.tracking

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackRepository
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.ElevationSample
import et.alpha.meret.core.model.SegmentSummary
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackDetail
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.TrackSummary
import et.alpha.meret.feature.tracking.service.FakeTrackingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackDetailViewModelTest {
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

    private fun viewModel() = TrackDetailViewModel(
        SavedStateHandle(mapOf(TrackDetailViewModel.TRACK_ID_ARG to TRACK_ID)),
        repository,
        tracking,
    )

    @Test
    fun `the detail shows the track's totals, segments and profile`() = runTest(dispatcher) {
        repository.detail.value = TrackDetail(track(), SUMMARY)

        val state = subscribe(viewModel())

        val ready = state() as TrackDetailUiState.Ready
        assertThat(ready.track.id).isEqualTo(TRACK_ID)
        assertThat(ready.track.progress).isEqualTo(TrackProgress.Finished)
        assertThat(ready.pointCount).isEqualTo(42)
        assertThat(ready.segments).isEqualTo(SUMMARY.segments)
        assertThat(ready.elevation).isEqualTo(SUMMARY.elevation)
        assertThat(repository.requested).containsExactly(TRACK_ID)
    }

    @Test
    fun `a track still recording follows the recorder's state`() = runTest(dispatcher) {
        repository.detail.value = TrackDetail(track(status = TrackStatus.ACTIVE), SUMMARY)
        tracking.state.value = TrackingState.Recording(TRACK_ID, isPaused = false)
        val state = subscribe(viewModel())

        assertThat((state() as TrackDetailUiState.Ready).track.progress)
            .isEqualTo(TrackProgress.Recording)

        tracking.state.value = TrackingState.Recording(TRACK_ID, isPaused = true)

        assertThat((state() as TrackDetailUiState.Ready).track.progress)
            .isEqualTo(TrackProgress.Paused)
    }

    @Test
    fun `a track that is gone is not found`() = runTest(dispatcher) {
        repository.detail.value = null

        val state = subscribe(viewModel())

        assertThat(state()).isEqualTo(TrackDetailUiState.NotFound)
    }

    private fun TestScope.subscribe(viewModel: TrackDetailViewModel): () -> TrackDetailUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return { viewModel.uiState.value }
    }

    private fun track(status: TrackStatus = TrackStatus.COMPLETED) = Track(
        id = TRACK_ID,
        projectId = "local",
        deviceId = "device",
        userId = "local",
        startedAt = 1_000L,
        status = status,
        pointCount = 42,
        updatedAt = 1_000L,
    )

    private class FakeRepository : TrackRepository {
        val detail = MutableStateFlow<TrackDetail?>(null)
        val requested = mutableListOf<String>()

        override fun observeTracks(): Flow<List<Track>> = emptyFlow()

        override fun observeTrackDetail(trackId: String): Flow<TrackDetail?> {
            requested += trackId
            return detail
        }

        override suspend fun discardTrack(trackId: String): AppResult<Unit> = error("not used")
    }

    private companion object {
        const val TRACK_ID = "walk"
        val SUMMARY =
            TrackSummary(
                segments =
                    listOf(
                        SegmentSummary(0, pointCount = 30, distanceMetres = 800.0, 600_000L),
                        SegmentSummary(1, pointCount = 12, distanceMetres = 300.0, 240_000L),
                    ),
                elevation =
                    listOf(ElevationSample(0.0, 2_400.0), ElevationSample(1_100.0, 2_430.0)),
            )
    }
}
