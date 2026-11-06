package et.alpha.meret.feature.waypoint

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.form.FieldError
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormSchema
import et.alpha.meret.core.model.form.LocalizedText
import et.alpha.meret.core.model.form.SelectOption
import et.alpha.meret.core.model.form.VisibleIf
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointDraft
import et.alpha.meret.domain.waypoint.WaypointDraftStore
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
    private val drafts = FakeDraftStore()
    private val photos = FakePhotoStore()
    private val sweeper = FakePhotoSweeper()
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
    fun `what was typed survives a new ViewModel through the stored draft`() = runTest(dispatcher) {
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
    fun `every change is in the stored draft at once, and a saved waypoint deletes it`() =
        runTest(dispatcher) {
            formSource = FixedForm(visitForm)
            val (viewModel, _) = subscribe()
            position.value = here

            viewModel.onTitleChange("Borehole")
            assertThat(drafts.draft?.title).isEqualTo("Borehole")
            viewModel.onCategoryTap(WaypointCategory.WATER_POINT)
            viewModel.onNotesChange("Pump broken")
            viewModel.onAnswerChange("present", FormAnswer.Choice("no"))
            viewModel.onNumberTextChange("people", "3")

            assertThat(drafts.draft)
                .isEqualTo(
                    WaypointDraft(
                        title = "Borehole",
                        categoryKey = "water_point",
                        notes = "Pump broken",
                        formTemplateId = "template-1",
                        formVersion = 2,
                        answersJson = """{"present":"no"}""",
                        numberTexts = mapOf("people" to "3"),
                    ),
                )

            viewModel.onSave()

            assertThat(store.saved).hasSize(1)
            assertThat(drafts.draft).isNull()
        }

    @Test
    fun `a sheet emptied by hand leaves no draft`() = runTest(dispatcher) {
        val (viewModel, _) = subscribe()
        viewModel.onTitleChange("B")
        assertThat(drafts.draft).isNotNull()

        viewModel.onTitleChange("")

        assertThat(drafts.draft).isNull()
    }

    @Test
    fun `a new ViewModel does not overwrite the draft before it has read it`() =
        runTest(dispatcher) {
            drafts.draft = WaypointDraft(title = "Borehole", notes = "Pump broken")
            val pending = CompletableDeferred<WaypointForm?>()
            formSource =
                object : WaypointFormSource {
                    override suspend fun currentForm(): WaypointForm? = pending.await()
                }
            val (_, state) = subscribe()

            assertThat(drafts.writes).isEqualTo(0)
            pending.complete(null)

            assertThat(state().title).isEqualTo("Borehole")
            assertThat(drafts.draft?.notes).isEqualTo("Pump broken")
        }

    @Test
    fun `answers given under another template are not restored, the text is`() =
        runTest(dispatcher) {
            drafts.draft =
                WaypointDraft(
                    title = "Borehole",
                    formTemplateId = "template-old",
                    formVersion = 1,
                    answersJson = """{"present":"yes"}""",
                    numberTexts = mapOf("people" to "4"),
                )
            formSource = FixedForm(visitForm)

            val (_, state) = subscribe()

            assertThat(state().title).isEqualTo("Borehole")
            assertThat(state().field("present").answer).isNull()
            assertThat(state().field("people").numberText).isEmpty()
        }

    @Test
    fun `a failed draft write keeps the sheet as typed`() = runTest(dispatcher) {
        drafts.failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.OutOfSpace))
        val (viewModel, state) = subscribe()

        viewModel.onTitleChange("Borehole")

        assertThat(state().title).isEqualTo("Borehole")
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
    fun `answers and shown problems survive a new ViewModel through the stored draft`() =
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

    @Test
    fun `a photo taken joins its field's answer and the draft`() = runTest(dispatcher) {
        formSource = FixedForm(photoForm)
        val (viewModel, state) = subscribe()

        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-1.jpg")
        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-2.jpg")

        assertThat(photos.captures).containsExactly("/cache/capture-1.jpg", "/cache/capture-2.jpg")
        val field = state().field("plot_photos")
        assertThat(field.answer).isEqualTo(FormAnswer.Photos(listOf("photo-1", "photo-2")))
        assertThat(field.photos)
            .containsExactly(
                FormPhoto("photo-1", "/photos/photo-1.jpg"),
                FormPhoto("photo-2", "/photos/photo-2.jpg"),
            ).inOrder()
        assertThat(drafts.draft?.answersJson)
            .isEqualTo("""{"plot_photos":["photo-1","photo-2"]}""")
    }

    @Test
    fun `a photo that cannot be stored changes nothing and says so`() = runTest(dispatcher) {
        formSource = FixedForm(photoForm)
        photos.failure = FakePhotoStore.Full
        val (viewModel, state) = subscribe()
        val events = collectEvents(viewModel)

        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-1.jpg")

        assertThat(state().field("plot_photos").answer).isNull()
        assertThat(events).containsExactly(WaypointCaptureEvent.PhotoNotSaved)
    }

    @Test
    fun `a capture for a field that takes no photos is not stored`() = runTest(dispatcher) {
        formSource = FixedForm(photoForm)
        val (viewModel, _) = subscribe()

        viewModel.onPhotoCaptured("present", "/cache/capture-1.jpg")
        viewModel.onPhotoCaptured("no_such_field", "/cache/capture-2.jpg")

        assertThat(photos.captures).isEmpty()
    }

    @Test
    fun `a capture that arrives before the draft is read is kept`() = runTest(dispatcher) {
        // After a process death with the camera open: the result comes back first.
        val pending = CompletableDeferred<WaypointForm?>()
        formSource =
            object : WaypointFormSource {
                override suspend fun currentForm(): WaypointForm? = pending.await()
            }
        val (viewModel, state) = subscribe()

        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-1.jpg")
        pending.complete(photoForm)

        assertThat(state().field("plot_photos").answer)
            .isEqualTo(FormAnswer.Photos(listOf("photo-1")))
    }

    @Test
    fun `a removed photo leaves the draft first, then its file goes`() = runTest(dispatcher) {
        formSource = FixedForm(photoForm)
        val (viewModel, state) = subscribe()
        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-1.jpg")
        viewModel.onPhotoCaptured("plot_photos", "/cache/capture-2.jpg")
        var draftAtDelete: String? = null
        photos.onDelete = { draftAtDelete = drafts.draft?.answersJson }

        viewModel.onPhotoRemove("plot_photos", "photo-1")

        assertThat(photos.deleted).containsExactly("photo-1")
        assertThat(draftAtDelete).isEqualTo("""{"plot_photos":["photo-2"]}""")
        assertThat(state().field("plot_photos").photos.map { it.id }).containsExactly("photo-2")

        viewModel.onPhotoRemove("plot_photos", "photo-2")

        assertThat(state().field("plot_photos").answer).isNull()
        assertThat(photos.deleted).containsExactly("photo-1", "photo-2").inOrder()
    }

    @Test
    fun `removing a photo the field does not hold deletes nothing`() = runTest(dispatcher) {
        formSource = FixedForm(photoForm)
        photos.add("photo-9")
        val (viewModel, _) = subscribe()

        viewModel.onPhotoRemove("plot_photos", "photo-9")

        assertThat(photos.deleted).isEmpty()
    }

    @Test
    fun `a restored draft's photos come back with their files`() = runTest(dispatcher) {
        photos.add("photo-1")
        drafts.draft =
            WaypointDraft(
                formTemplateId = "template-1",
                formVersion = 2,
                answersJson = """{"plot_photos":["photo-1","photo-gone"]}""",
            )
        formSource = FixedForm(photoForm)

        val (_, state) = subscribe()

        assertThat(state().field("plot_photos").photos)
            .containsExactly(
                FormPhoto("photo-1", "/photos/photo-1.jpg"),
                FormPhoto("photo-gone", null),
            ).inOrder()
    }

    @Test
    fun `saved photos go to the store with the waypoint, and the sheet starts empty`() =
        runTest(dispatcher) {
            formSource = FixedForm(photoForm)
            position.value = here
            val (viewModel, state) = subscribe()
            viewModel.onAnswerChange("present", FormAnswer.Choice("no"))
            viewModel.onPhotoCaptured("plot_photos", "/cache/capture-1.jpg")

            viewModel.onSave()

            assertThat(
                store.saved
                    .single()
                    .photos
                    .map { it.id },
            ).containsExactly("photo-1")
            assertThat(state().field("plot_photos").photos).isEmpty()
        }

    @Test
    fun `opening the sheet sweeps orphaned photos once, after the draft is restored`() =
        runTest(dispatcher) {
            drafts.draft = WaypointDraft(title = "Borehole", notes = "Pump broken")
            var loadsAtSweep = -1
            sweeper.onSweep = { loadsAtSweep = drafts.loads }

            val (_, state) = subscribe()

            assertThat(sweeper.sweeps).isEqualTo(1)
            assertThat(loadsAtSweep).isEqualTo(1)
            assertThat(state().title).isEqualTo("Borehole")
            assertThat(drafts.draft?.title).isEqualTo("Borehole")
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
                saveWaypoint = SaveWaypointUseCase(store, photos),
                drafts = drafts,
                formSource = formSource,
                photos = WaypointPhotoFiles(photos, sweeper),
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

    private val photoForm =
        visitForm.copy(
            schema =
                visitForm.schema.copy(
                    fields =
                        visitForm.schema.fields +
                            FormField.Photo(
                                key = "plot_photos",
                                label = LocalizedText.of("Photos of the plot"),
                                maxCount = 3,
                            ),
                ),
        )

    /** One draft, as Room keeps it. */
    private class FakeDraftStore : WaypointDraftStore {
        var draft: WaypointDraft? = null
        var writes = 0
        var loads = 0
        var failure: AppResult.Failure? = null

        override suspend fun load(): WaypointDraft? {
            loads++
            return draft
        }

        override suspend fun save(draft: WaypointDraft): AppResult<Unit> {
            writes++
            failure?.let { return it }
            this.draft = draft
            return AppResult.Success(Unit)
        }

        override suspend fun clear(): AppResult<Unit> {
            writes++
            failure?.let { return it }
            draft = null
            return AppResult.Success(Unit)
        }
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
}
