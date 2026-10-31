package et.alpha.meret.feature.map

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.data.tracking.EarlierTracksRepository
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EarlierTracksViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeEarlierTracks()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing loads until the map reports where it is`() = runViewModelTest { viewModel ->
        viewModel.lines.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.onVisibleAreaChanged(null)
            runCurrent()

            assertThat(repository.requested).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tracks around the screen reach the map and follow Room`() = runViewModelTest { viewModel ->
        viewModel.lines.test {
            assertThat(awaitItem()).isEmpty()

            viewModel.onVisibleAreaChanged(SCREEN)
            runCurrent()
            val loaded = repository.requested.single()
            assertThat(SCREEN.corners.all { it in loaded }).isTrue()

            repository.lines.value = listOf(LINE)
            assertThat(awaitItem()).containsExactly(LINE)

            repository.lines.value = emptyList()
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a small pan asks Room nothing, a long one loads anew`() = runViewModelTest { viewModel ->
        backgroundScope.launch { viewModel.lines.collect {} }

        viewModel.onVisibleAreaChanged(SCREEN)
        advanceTimeBy(THROTTLE_PASSED_MS)
        viewModel.onVisibleAreaChanged(SCREEN.shifted(latitude = 0.01))
        advanceTimeBy(THROTTLE_PASSED_MS)
        assertThat(repository.requested).hasSize(1)

        val farAway = SCREEN.shifted(latitude = 1.0)
        viewModel.onVisibleAreaChanged(farAway)
        advanceTimeBy(THROTTLE_PASSED_MS)
        assertThat(repository.requested).hasSize(2)
        assertThat(farAway.corners.all { it in repository.requested.last() }).isTrue()
    }

    @Test
    fun `a burst of camera rests loads once, then the last one`() = runViewModelTest { viewModel ->
        backgroundScope.launch { viewModel.lines.collect {} }
        val last = SCREEN.shifted(latitude = 3.0)

        viewModel.onVisibleAreaChanged(SCREEN)
        viewModel.onVisibleAreaChanged(SCREEN.shifted(latitude = 1.0))
        viewModel.onVisibleAreaChanged(SCREEN.shifted(latitude = 2.0))
        viewModel.onVisibleAreaChanged(last)
        assertThat(repository.requested).hasSize(1)

        advanceTimeBy(THROTTLE_PASSED_MS)
        assertThat(repository.requested).hasSize(2)
        assertThat(last.corners.all { it in repository.requested.last() }).isTrue()
    }

    private fun runViewModelTest(body: suspend TestScope.(EarlierTracksViewModel) -> Unit) =
        runTest(dispatcher) {
            val viewModel = EarlierTracksViewModel(repository)
            try {
                body(viewModel)
            } finally {
                viewModel.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }

    private fun BoundingBox.shifted(latitude: Double) = copy(
        minLatitude = minLatitude + latitude,
        maxLatitude = maxLatitude + latitude,
    )

    /** Hands out [lines] for any area, and remembers which areas were asked for. */
    private class FakeEarlierTracks : EarlierTracksRepository {
        val lines = MutableStateFlow<List<TrackLine>>(emptyList())
        val requested = mutableListOf<BoundingBox>()

        override fun observeEarlierTracks(area: BoundingBox): Flow<List<TrackLine>> = flow {
            requested += area
            emitAll(lines)
        }
    }

    private companion object {
        /** About 4 km by 4 km around Addis Ababa: the screen at street level. */
        val SCREEN = BoundingBox(9.0, 38.72, 9.04, 38.76)
        val LINE = TrackLine(listOf(listOf(LatLng(9.01, 38.74), LatLng(9.02, 38.74))))

        /** Longer than the view model's 1 s throttle between area loads. */
        const val THROTTLE_PASSED_MS = 1_001L
    }
}
