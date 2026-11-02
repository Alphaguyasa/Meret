package et.alpha.meret.feature.waypoint

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.form.FieldError
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormSchema
import et.alpha.meret.core.model.form.LocalizedText
import et.alpha.meret.core.model.form.SelectOption
import et.alpha.meret.core.model.form.VisibleIf
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointForm
import et.alpha.meret.domain.waypoint.WaypointFormSource
import et.alpha.meret.domain.waypoint.WaypointStore
import kotlinx.coroutines.CompletableDeferred
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
    private var formSource: WaypointFormSource = NoForm

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
        assertThat(events).containsExactly(WaypointCaptureEvent.NotSaved(NotSavedReason.NoFix))
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

        assertThat(events).containsExactly(WaypointCaptureEvent.NotSaved(NotSavedReason.Storage))
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

    @Test
    fun `without a form the sheet has none and saves no form columns`() = runTest(dispatcher) {
        val (viewModel, state) = subscribe()
        position.value = here

        assertThat(state().form).isEqualTo(FormUiState.None)
        viewModel.onSave()

        assertThat(store.saved.single().formTemplateId).isNull()
        assertThat(store.saved.single().formData).isNull()
    }

    @Test
    fun `Save waits until the form has been read`() = runTest(dispatcher) {
        val pending = CompletableDeferred<WaypointForm?>()
        formSource =
            object : WaypointFormSource {
                override suspend fun currentForm(): WaypointForm? = pending.await()
            }
        val (viewModel, state) = subscribe()
        position.value = here

        assertThat(state().form).isEqualTo(FormUiState.Loading)
        assertThat(state().canSave).isFalse()
        viewModel.onSave()
        assertThat(store.saved).isEmpty()

        pending.complete(visitForm)

        assertThat(state().form).isInstanceOf(FormUiState.Ready::class.java)
        assertThat(state().canSave).isTrue()
    }

    @Test
    fun `the form shows only the fields its answers make visible`() = runTest(dispatcher) {
        formSource = FixedForm(visitForm)
        val (viewModel, state) = subscribe()

        assertThat(state().readyKeys()).containsExactly("present", "people").inOrder()

        viewModel.onAnswerChange("present", FormAnswer.Choice("yes"))

        assertThat(state().readyKeys()).containsExactly("present", "name", "people").inOrder()
        assertThat(state().field("present").answer).isEqualTo(FormAnswer.Choice("yes"))
    }

    @Test
    fun `a refused Save shows each problem and writes nothing`() = runTest(dispatcher) {
        formSource = FixedForm(visitForm)
        val (viewModel, state) = subscribe()
        val events = collectEvents(viewModel)
        position.value = here
        viewModel.onAnswerChange("present", FormAnswer.Choice("yes"))
        viewModel.onNumberTextChange("people", "lots")

        assertThat(state().field("name").error).isNull()
        viewModel.onSave()

        assertThat(store.saved).isEmpty()
        assertThat(
            events,
        ).containsExactly(WaypointCaptureEvent.NotSaved(NotSavedReason.FormIncomplete))
        assertThat(state().field("name").error).isEqualTo(FieldError.Required)
        assertThat(state().field("people").error).isEqualTo(FieldError.WrongType)
        assertThat(state().field("present").error).isNull()

        viewModel.onAnswerChange("name", FormAnswer.Text("Abebe"))

        assertThat(state().field("name").error).isNull()
    }

    @Test
    fun `answers are saved under the form, the number box as last typed`() = runTest(dispatcher) {
        formSource = FixedForm(visitForm)
        val (viewModel, state) = subscribe()
        val events = collectEvents(viewModel)
        position.value = here
        viewModel.onAnswerChange("present", FormAnswer.Choice("yes"))
        viewModel.onAnswerChange("name", FormAnswer.Text("Abebe"))
        viewModel.onNumberTextChange("people", "2,5")
        viewModel.onNumberTextChange("people", "5")

        viewModel.onSave()

        assertThat(events).containsExactly(WaypointCaptureEvent.Saved)
        val saved = store.saved.single()
        assertThat(saved.formTemplateId).isEqualTo("template-1")
        assertThat(saved.formVersion).isEqualTo(2)
        assertThat(
            saved.formData,
        ).isEqualTo("""{"present":"yes","name":"Abebe","people":5.0}""")
        assertThat(state().field("present").answer).isNull()
        assertThat(state().field("people").numberText).isEmpty()
    }

    @Test
    fun `a hidden field's answer is not saved`() = runTest(dispatcher) {
        formSource = FixedForm(visitForm)
        val (viewModel, _) = subscribe()
        position.value = here
        viewModel.onAnswerChange("present", FormAnswer.Choice("yes"))
        viewModel.onAnswerChange("name", FormAnswer.Text("Abebe"))
        viewModel.onAnswerChange("present", FormAnswer.Choice("no"))

        viewModel.onSave()

        assertThat(store.saved.single().formData).isEqualTo("""{"present":"no"}""")
    }

    @Test
    fun `answers and shown problems survive a new ViewModel on the same saved state`() =
        runTest(dispatcher) {
            formSource = FixedForm(visitForm)
            val (first, _) = subscribe()
            position.value = here
            first.onAnswerChange("present", FormAnswer.Choice("yes"))
            first.onNumberTextChange("people", "4.")
            first.onSave()

            val (_, state) = subscribe()

            assertThat(state().field("present").answer).isEqualTo(FormAnswer.Choice("yes"))
            assertThat(state().field("people").numberText).isEqualTo("4.")
            assertThat(state().field("name").error).isEqualTo(FieldError.Required)
        }

    @Test
    fun `an answer to a key the form does not have is ignored`() = runTest(dispatcher) {
        formSource = FixedForm(visitForm)
        val (viewModel, state) = subscribe()

        viewModel.onAnswerChange("nonexistent", FormAnswer.Text("x"))
        viewModel.onNumberTextChange("present", "3")

        assertThat(state().field("present").answer).isNull()
        assertThat(state().readyKeys()).containsExactly("present", "people").inOrder()
    }

    @Test
    fun `a number box is read strictly, a thousands comma refused`() {
        assertThat(parseNumber(" 2.5 ")).isEqualTo(2.5)
        assertThat(parseNumber("1,500")).isNull()
        assertThat(parseNumber("-3")).isEqualTo(-3.0)
        assertThat(parseNumber("4.")).isEqualTo(4.0)
        assertThat(parseNumber("lots")).isNull()
        assertThat(parseNumber("NaN")).isNull()
        assertThat(formatLimit(30.0)).isEqualTo("30")
        assertThat(formatLimit(0.25)).isEqualTo("0.25")
    }

    private fun CaptureState.readyKeys(): List<String> =
        (form as FormUiState.Ready).fields.map { it.field.key }

    private fun CaptureState.field(key: String): FormFieldUiState =
        (form as FormUiState.Ready).fields.single { it.field.key == key }

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
                formSource = formSource,
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

    private object NoForm : WaypointFormSource {
        override suspend fun currentForm(): WaypointForm? = null
    }

    private class FixedForm(
        private val form: WaypointForm,
    ) : WaypointFormSource {
        override suspend fun currentForm(): WaypointForm = form
    }

    private val visitForm =
        WaypointForm(
            templateId = "template-1",
            version = 2,
            name = "Visit",
            schema =
                FormSchema(
                    format = 1,
                    fields =
                        listOf(
                            FormField.Select(
                                key = "present",
                                label = LocalizedText.of("Present?"),
                                required = true,
                                options =
                                    listOf(
                                        SelectOption("yes", LocalizedText.of("Yes")),
                                        SelectOption("no", LocalizedText.of("No")),
                                    ),
                            ),
                            FormField.Text(
                                key = "name",
                                label = LocalizedText.of("Name"),
                                required = true,
                                visibleIf = VisibleIf.Equals("present", "yes"),
                            ),
                            FormField.Number(
                                key = "people",
                                label = LocalizedText.of("People"),
                                integer = true,
                                min = 1.0,
                            ),
                        ),
                ),
        )

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
