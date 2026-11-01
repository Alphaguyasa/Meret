package et.alpha.meret.feature.waypoint

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private typealias CaptureState = WaypointCaptureUiState

@OptIn(ExperimentalCoroutinesApi::class)
class WaypointCaptureViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val position = MutableStateFlow<DevicePosition?>(null)
    private val tracking = FakeTrackingController()
    private val store = FakeWaypointStore()
    private val savedState = SavedStateHandle()

    private val here =
        DevicePosition(
            position = LatLng(9.0301, 38.7402),
            accuracyMetres = 4.6,
            isLive = true,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `without a live fix the sheet waits and Save is off`() = runTest(dispatcher) {
        val (viewModel, state) = subscribe()

        assertThat(state().fix).isEqualTo(CaptureFix.Waiting)
        assertThat(state().canSave).isFalse()

        position.value = here.copy(isLive = false)

        assertThat(state().fix).isEqualTo(CaptureFix.Waiting)
        assertThat(viewModel.uiState.value.canSave).isFalse()
    }

    @Test
    fun `a live fix is shown and enables Save`() = runTest(dispatcher) {
        val (_, state) = subscribe()

        position.value = here

        assertThat(state().fix).isEqualTo(CaptureFix.Located(9.0301, 38.7402, 4.6))
        assertThat(state().canSave).isTrue()
    }

    @Test
    fun `Save while idle writes the waypoint at the fix, then the sheet starts empty`() =
        runTest(dispatcher) {
            val (viewModel, state) = subscribe()
            val events = collectEvents(viewModel)
            position.value = here
            viewModel.onTitleChange(" Borehole ")
            viewModel.onCategoryTap(WaypointCategory.WATER_POINT)
            viewModel.onNotesChange("Pump broken")

            viewModel.onSave()

            assertThat(store.saved)
                .containsExactly(
                    NewWaypoint(
                        position = LatLng(9.0301, 38.7402),
                        accuracyMetres = 4.6,
                        altitudeMetres = null,
                        trackId = null,
                        category = WaypointCategory.WATER_POINT,
                        title = "Borehole",
                        notes = "Pump broken",
                    ),
                )
            assertThat(events).containsExactly(WaypointCaptureEvent.Saved)
            assertThat(state().title).isEmpty()
            assertThat(state().notes).isEmpty()
            assertThat(state().category).isNull()
        }

    @Test
    fun `Save while recording attaches the waypoint to the track`() = runTest(dispatcher) {
        tracking.state.value = TrackingState.Recording("track-7", isPaused = true)
        val (viewModel, state) = subscribe()
        position.value = here

        assertThat(state().onTrack).isTrue()
        viewModel.onSave()

        assertThat(store.saved.single().trackId).isEqualTo("track-7")
    }

    @Test
    fun `Save without a fix writes nothing and says so`() = runTest(dispatcher) {
        val (viewModel, state) = subscribe()
        val events = collectEvents(viewModel)
        viewModel.onTitleChange("Borehole")

        viewModel.onSave()

        assertThat(store.saved).isEmpty()
        assertThat(events).containsExactly(WaypointCaptureEvent.NotSaved(noFix = true))
        assertThat(state().title).isEqualTo("Borehole")
    }

    @Test
    fun `a failed write keeps what was typed`() = runTest(dispatcher) {
        store.failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.OutOfSpace))
        val (viewModel, state) = subscribe()
        val events = collectEvents(viewModel)
        position.value = here
        viewModel.onTitleChange("Borehole")

        viewModel.onSave()

        assertThat(events).containsExactly(WaypointCaptureEvent.NotSaved(noFix = false))
        assertThat(state().title).isEqualTo("Borehole")
    }

    @Test
    fun `tapping the picked category clears it`() = runTest(dispatcher) {
        val (viewModel, state) = subscribe()

        viewModel.onCategoryTap(WaypointCategory.FARM_PLOT)
        assertThat(state().category).isEqualTo(WaypointCategory.FARM_PLOT)
        viewModel.onCategoryTap(WaypointCategory.LANDMARK)
        assertThat(state().category).isEqualTo(WaypointCategory.LANDMARK)
        viewModel.onCategoryTap(WaypointCategory.LANDMARK)

        assertThat(state().category).isNull()
    }

    @Test
    fun `what was typed survives a new ViewModel on the same saved state`() = runTest(dispatcher) {
        val (first, _) = subscribe()
        first.onTitleChange("Borehole")
        first.onNotesChange("Pump broken")
        first.onCategoryTap(WaypointCategory.WATER_POINT)

        val (_, state) = subscribe()

        assertThat(state().title).isEqualTo("Borehole")
        assertThat(state().notes).isEqualTo("Pump broken")
        assertThat(state().category).isEqualTo(WaypointCategory.WATER_POINT)
    }

    private fun TestScope.subscribe(): Pair<WaypointCaptureViewModel, () -> CaptureState> {
        val viewModel =
            WaypointCaptureViewModel(
                devicePosition =
                    object : DevicePositionRepository {
                        override fun observePosition() = position
                    },
                tracking = tracking,
                saveWaypoint = SaveWaypointUseCase(store),
                savedState = savedState,
            )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel to { viewModel.uiState.value }
    }

    private fun TestScope.collectEvents(
        viewModel: WaypointCaptureViewModel,
    ): List<WaypointCaptureEvent> {
        val events = mutableListOf<WaypointCaptureEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }

    private class FakeWaypointStore : WaypointStore {
        val saved = mutableListOf<NewWaypoint>()
        var failure: AppResult.Failure? = null

        override suspend fun save(waypoint: NewWaypoint): AppResult<String> {
            failure?.let { return it }
            saved += waypoint
            return AppResult.Success("waypoint-${saved.size}")
        }
    }

    private class FakeTrackingController : TrackingController {
        override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)
        override val activeTrack = MutableStateFlow<Track?>(null)
        override val activeLine = MutableStateFlow<TrackLine?>(null)
        override val activeSegmentCount = MutableStateFlow<Int?>(null)
        override val health = MutableStateFlow(TrackingHealth())

        override suspend fun start(trackId: String): AppResult<Unit> = error("the service's call")

        override suspend fun reopen(trackId: String): AppResult<Unit> = error("the service's call")

        override suspend fun restore(): AppResult<TrackingState> = error("the service's call")

        override suspend fun interruptedTrackId(): String? = error("the service's call")

        override suspend fun pause(): Unit = error("the service's call")

        override suspend fun resume(): AppResult<Unit> = error("the service's call")

        override suspend fun stop(): Unit = error("the service's call")
    }
}
