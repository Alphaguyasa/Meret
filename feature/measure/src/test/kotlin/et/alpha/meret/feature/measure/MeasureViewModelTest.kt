package et.alpha.meret.feature.measure

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.measure.MeasureRingDraft
import et.alpha.meret.domain.measure.MeasurementStore
import et.alpha.meret.domain.measure.NewMeasurement
import et.alpha.meret.domain.measure.SaveMeasurementUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val draft = FakeDraft()
    private val store = FakeStore()
    private val viewModel by lazy {
        MeasureViewModel(draft, SaveMeasurementUseCase(store), TestDispatchers(dispatcher))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a corner goes to the draft, and the screen reads it back from there`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.onAddCorner(A)
            viewModel.onAddCorner(B)

            assertThat(draft.ring.value).containsExactly(A, B).inOrder()
            assertThat(viewModel.uiState.value.vertices).containsExactly(A, B).inOrder()
        }

    @Test
    fun `no map under the crosshair adds nothing, and neither does a second tap in place`() =
        runTest(dispatcher) {
            viewModel.onAddCorner(null)
            viewModel.onAddCorner(A)
            viewModel.onAddCorner(A)

            assertThat(draft.ring.value).containsExactly(A)
        }

    @Test
    fun `a ring already in the draft comes back when the screen opens`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)

        backgroundScope.launch { viewModel.uiState.collect {} }

        assertThat(viewModel.uiState.value.vertices).containsExactly(A, B, C).inOrder()
        assertThat(viewModel.uiState.value.canSave).isTrue()
    }

    @Test
    fun `two corners have a length and no area, and cannot be saved`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B)
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value

        assertThat(state.areaSquareMetres).isEqualTo(0.0)
        assertThat(state.perimeterMetres).isGreaterThan(1_000.0)
        assertThat(state.perimeterMetres).isLessThan(1_200.0)
        assertThat(state.canSave).isFalse()
    }

    @Test
    fun `three corners have an area and a closed perimeter`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value

        // Half of a cell about 1.1 km on a side.
        assertThat(state.areaSquareMetres).isGreaterThan(600_000.0)
        assertThat(state.areaSquareMetres).isLessThan(610_000.0)
        assertThat(state.perimeterMetres).isGreaterThan(3_500.0)
        assertThat(state.perimeterMetres).isLessThan(3_900.0)
    }

    @Test
    fun `undo takes the last corner, and clear takes them all`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)

        viewModel.onUndo()
        assertThat(draft.ring.value).containsExactly(A, B).inOrder()

        viewModel.onClear()
        assertThat(draft.ring.value).isEmpty()
    }

    @Test
    fun `save writes a tapped plot and only then forgets the ring`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)
        val events = mutableListOf<MeasureEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        viewModel.onSave("  Kebele 4  ")

        val saved = store.saved.single()
        assertThat(saved.name).isEqualTo("Kebele 4")
        assertThat(saved.method).isEqualTo(MeasurementMethod.TAPPED)
        assertThat(store.ringWhenSaved).containsExactly(A, B, C).inOrder()
        assertThat(draft.ring.value).isEmpty()
        assertThat(events).containsExactly(MeasureEvent.Saved)
    }

    @Test
    fun `a ring that is not a plot is refused and kept`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B)
        val events = mutableListOf<MeasureEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        viewModel.onSave("")

        assertThat(store.saved).isEmpty()
        assertThat(draft.ring.value).containsExactly(A, B).inOrder()
        assertThat(events).containsExactly(MeasureEvent.NotAPlot)
    }

    @Test
    fun `a failed write keeps the ring and says so`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)
        store.failure = AppError.Storage(AppError.Storage.Kind.WriteFailed, null)
        val events = mutableListOf<MeasureEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        viewModel.onSave("")

        assertThat(draft.ring.value).containsExactly(A, B, C).inOrder()
        assertThat(events).containsExactly(MeasureEvent.StorageFailed)
    }

    @Test
    fun `a corner the draft could not store is reported`() = runTest(dispatcher) {
        draft.failing = true
        val events = mutableListOf<MeasureEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }

        viewModel.onAddCorner(A)

        assertThat(events).containsExactly(MeasureEvent.StorageFailed)
    }

    @Test
    fun `a second save while the first is writing does nothing`() = runTest(dispatcher) {
        draft.ring.value = listOf(A, B, C)
        val release = CompletableDeferred<Unit>()
        store.gate = release

        viewModel.onSave("")
        viewModel.onSave("")
        release.complete(Unit)

        assertThat(store.saved).hasSize(1)
    }

    private inner class FakeStore : MeasurementStore {
        val saved = mutableListOf<NewMeasurement>()
        var ringWhenSaved: List<LatLng> = emptyList()
        var failure: AppError? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun save(measurement: NewMeasurement): AppResult<String> {
            gate?.await()
            ringWhenSaved = draft.ring.value
            failure?.let { return AppResult.Failure(it) }
            saved += measurement
            return AppResult.Success("m-1")
        }
    }

    private class FakeDraft : MeasureRingDraft {
        val ring = MutableStateFlow<List<LatLng>>(emptyList())
        var failing = false

        override val vertices = ring

        override suspend fun update(transform: (List<LatLng>) -> List<LatLng>): AppResult<Unit> {
            if (failing) {
                return AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, null))
            }
            ring.value = transform(ring.value)
            return AppResult.Success(Unit)
        }
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
        override val mainImmediate = dispatcher
    }

    private companion object {
        val A = LatLng(9.03, 38.74)
        val B = LatLng(9.03, 38.75)
        val C = LatLng(9.04, 38.75)
    }
}
