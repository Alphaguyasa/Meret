package et.alpha.meret.feature.waypoint

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.form.FieldError
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormAnswersJson
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormRules
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointForm
import et.alpha.meret.domain.waypoint.WaypointFormSource
import et.alpha.meret.domain.waypoint.WaypointInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * The waypoint sheet over the map: where the phone is, what the worker typed, and Save.
 *
 * Save writes the waypoint to Room at the live fix of that moment, attached to the track
 * being recorded if there is one, and nothing else: no network. Without a live fix it is
 * refused (see [SaveWaypointUseCase]).
 *
 * The project's form (until sync, the bundled farm visit) is shown under the notes. Its
 * answers must pass the form's checks before Save writes anything; the problems are shown
 * from the first refused Save on, and then follow every change.
 *
 * The text, category and answers are kept in [SavedStateHandle], so a rotation or a process
 * death while the app is in the background keeps them. A dismissed sheet keeps them too, for
 * a swipe made by mistake; a saved one starts empty. Draft autosave to Room is item 32.
 */
@HiltViewModel
class WaypointCaptureViewModel
    @Inject
    constructor(
        devicePosition: DevicePositionRepository,
        private val tracking: TrackingController,
        private val saveWaypoint: SaveWaypointUseCase,
        private val savedState: SavedStateHandle,
        formSource: WaypointFormSource,
    ) : ViewModel() {
        private val fix: StateFlow<DevicePosition?> =
            devicePosition
                .observePosition()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = null,
                )

        private val input =
            MutableStateFlow(
                WaypointInput(
                    title = savedState.get<String>(KEY_TITLE).orEmpty(),
                    category = WaypointCategory.fromKey(savedState.get<String>(KEY_CATEGORY)),
                    notes = savedState.get<String>(KEY_NOTES).orEmpty(),
                ),
            )

        private val draft = MutableStateFlow(FormDraft())

        init {
            viewModelScope.launch {
                val form = formSource.currentForm()
                draft.value = if (form == null) FormDraft(loaded = true) else restoreDraft(form)
            }
        }

        val uiState: StateFlow<WaypointCaptureUiState> =
            combine(fix, tracking.state, input, draft) { here, recording, typed, answers ->
                WaypointCaptureUiState(
                    fix = here.toCaptureFix(),
                    onTrack = recording is TrackingState.Recording,
                    title = typed.title,
                    category = typed.category,
                    notes = typed.notes,
                    form = answers.toUiState(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = WaypointCaptureUiState(),
            )

        private val mutableEvents = Channel<WaypointCaptureEvent>(Channel.BUFFERED)

        /** One-shot outcomes of Save, for the route. */
        val events: Flow<WaypointCaptureEvent> = mutableEvents.receiveAsFlow()

        private var saveJob: Job? = null

        fun onTitleChange(title: String) {
            savedState[KEY_TITLE] = title
            input.update { it.copy(title = title) }
        }

        fun onNotesChange(notes: String) {
            savedState[KEY_NOTES] = notes
            input.update { it.copy(notes = notes) }
        }

        /** Picks [category], or clears it when it is the one already picked. */
        fun onCategoryTap(category: WaypointCategory) {
            val picked = category.takeUnless { it == input.value.category }
            savedState[KEY_CATEGORY] = picked?.key
            input.update { it.copy(category = picked) }
        }

        /**
         * The answer to the form field [key], or null to clear it. For every field type but
         * number, whose box text goes through [onNumberTextChange].
         */
        fun onAnswerChange(
            key: String,
            answer: FormAnswer?,
        ) {
            val current = draft.value
            if (current.form?.schema?.field(key) == null) return
            val answers =
                when (answer) {
                    null -> current.answers - key
                    else -> current.answers + (key to answer)
                }
            savedState[KEY_FORM_ANSWERS] = FormAnswersJson.encode(answers)
            draft.value = current.copy(answers = answers)
        }

        /** What is in a number field's box, as typed: it may not be a number (yet). */
        fun onNumberTextChange(
            key: String,
            text: String,
        ) {
            val current = draft.value
            if (current.form?.schema?.field(key) !is FormField.Number) return
            savedState[NUMBER_PREFIX + key] = text
            draft.value = current.copy(numberTexts = current.numberTexts + (key to text))
        }

        /**
         * A second tap while a save is being written is ignored: it would save twice. Nothing is
         * saved while the form is still being read, or the waypoint would miss it.
         */
        fun onSave() {
            if (saveJob?.isActive == true || !draft.value.loaded) return
            saveJob =
                viewModelScope.launch {
                    val trackId = (tracking.state.value as? TrackingState.Recording)?.trackId
                    val form = draft.value
                    val toSave =
                        input.value.copy(
                            form = form.form,
                            answers = form.effectiveAnswers(),
                        )
                    val event =
                        when (val result = saveWaypoint(fix.value, trackId, toSave)) {
                            is AppResult.Success -> {
                                clearInput()
                                WaypointCaptureEvent.Saved
                            }

                            is AppResult.Failure -> {
                                WaypointCaptureEvent.NotSaved(result.error.toReason())
                            }
                        }
                    if (event == WaypointCaptureEvent.NotSaved(NotSavedReason.FormIncomplete)) {
                        savedState[KEY_SHOW_ERRORS] = true
                        draft.update { it.copy(showErrors = true) }
                    }
                    mutableEvents.send(event)
                }
        }

        private fun clearInput() {
            savedState[KEY_TITLE] = ""
            savedState[KEY_NOTES] = ""
            savedState.remove<String>(KEY_CATEGORY)
            savedState.remove<String>(KEY_FORM_ANSWERS)
            savedState.remove<Boolean>(KEY_SHOW_ERRORS)
            savedState.keys().filter { it.startsWith(NUMBER_PREFIX) }.forEach {
                savedState.remove<String>(it)
            }
            input.value = WaypointInput()
            draft.update { FormDraft(form = it.form, loaded = it.loaded) }
        }

        /** The form with what was entered before a rotation or a process death. */
        private fun restoreDraft(form: WaypointForm): FormDraft {
            val decoded =
                FormAnswersJson.decode(
                    savedState.get<String>(KEY_FORM_ANSWERS),
                    form.schema,
                )
            val answers = (decoded as? FormAnswersJson.Decoded.Answers)?.answers.orEmpty()
            val numberTexts =
                form.schema.fields
                    .filterIsInstance<FormField.Number>()
                    .mapNotNull { field ->
                        savedState.get<String>(NUMBER_PREFIX + field.key)?.let { field.key to it }
                    }.toMap()
            return FormDraft(
                form = form,
                loaded = true,
                answers = answers,
                numberTexts = numberTexts,
                showErrors = savedState.get<Boolean>(KEY_SHOW_ERRORS) == true,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val KEY_TITLE = "waypoint_title"
            const val KEY_NOTES = "waypoint_notes"
            const val KEY_CATEGORY = "waypoint_category"
            const val KEY_FORM_ANSWERS = "waypoint_form_answers"
            const val KEY_SHOW_ERRORS = "waypoint_form_show_errors"
            const val NUMBER_PREFIX = "waypoint_form_number:"
        }
    }

/**
 * The form as it is being filled in. Number fields keep the box text in [numberTexts], since
 * "1." or "-" is on its way to a number but is not one; every other answer is in [answers].
 */
internal data class FormDraft(
    val form: WaypointForm? = null,
    /** The form source has answered, with a form or with none. */
    val loaded: Boolean = false,
    val answers: Map<String, FormAnswer> = emptyMap(),
    val numberTexts: Map<String, String> = emptyMap(),
    val showErrors: Boolean = false,
) {
    /**
     * The answers to check and save. A number box with text that is not a number becomes a
     * text answer, which the form's checks report as the wrong type, never a silent blank.
     */
    fun effectiveAnswers(): Map<String, FormAnswer> {
        val numbers =
            numberTexts
                .filterValues { it.isNotBlank() }
                .mapValues { (_, text) ->
                    parseNumber(text)?.let(FormAnswer::Number) ?: FormAnswer.Text(text)
                }
        return answers.filterKeys { it !in numberTexts } + numbers
    }

    fun toUiState(): FormUiState {
        val schema = form?.schema
        return when {
            !loaded -> {
                FormUiState.Loading
            }

            form == null || schema == null -> {
                FormUiState.None
            }

            else -> {
                val effective = effectiveAnswers()
                val visible = FormRules.visibleKeys(schema, effective)
                val errors = if (showErrors) FormRules.validate(schema, effective) else emptyMap()
                FormUiState.Ready(
                    templateId = form.templateId,
                    name = form.name,
                    fields =
                        schema.fields.filter { it.key in visible }.map { field ->
                            FormFieldUiState(
                                field = field,
                                answer = effective[field.key],
                                numberText =
                                    numberTexts[field.key] ?: effective[field.key].numberText(),
                                error = errors[field.key],
                            )
                        },
                )
            }
        }
    }
}

/** Box text for a stored number answer: no ".0" on a whole number. */
private fun FormAnswer?.numberText(): String {
    val value = (this as? FormAnswer.Number)?.value ?: return ""
    return if (value == Math.rint(value) && abs(value) < MAX_EXACT_LONG) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

/**
 * The number in a box, or null. A comma is not read as a decimal point: in Ethiopia it
 * separates thousands, so "1,500" must be refused ("Enter a number"), never saved as 1.5.
 */
internal fun parseNumber(text: String): Double? =
    text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

private const val MAX_EXACT_LONG = 1e15

private fun AppError.toReason(): NotSavedReason = when (this) {
    is AppError.Gps -> NotSavedReason.NoFix
    is AppError.Invalid -> NotSavedReason.FormIncomplete
    else -> NotSavedReason.Storage
}

/** Everything the waypoint sheet shows. Saving is always possible once [fix] is located. */
data class WaypointCaptureUiState(
    val fix: CaptureFix = CaptureFix.Waiting,
    /** A track is being recorded, and the waypoint will belong to it. */
    val onTrack: Boolean = false,
    val title: String = "",
    val category: WaypointCategory? = null,
    val notes: String = "",
    val form: FormUiState = FormUiState.Loading,
) {
    /** A fix, and the form read (so the waypoint cannot be saved without it). */
    val canSave: Boolean get() = fix is CaptureFix.Located && form != FormUiState.Loading
}

/** The form part of the sheet. */
sealed interface FormUiState {
    /** The form is being read; Save waits for it. */
    data object Loading : FormUiState

    /** The project has no form: the sheet is title, category and notes only. */
    data object None : FormUiState

    data class Ready(
        val templateId: String,
        /** The template's own name; bundled templates show a string resource instead. */
        val name: String,
        /** The fields that show for the answers so far, in form order. */
        val fields: List<FormFieldUiState>,
    ) : FormUiState
}

/** One visible field, its answer, and its problem once problems are being shown. */
data class FormFieldUiState(
    val field: FormField,
    val answer: FormAnswer?,
    /** A number field's box text; empty for other types. */
    val numberText: String = "",
    val error: FieldError? = null,
)

/** Where the waypoint would be saved. */
sealed interface CaptureFix {
    /** No live fix yet: none at all, or only the platform's cached one. */
    data object Waiting : CaptureFix

    data class Located(
        val latitude: Double,
        val longitude: Double,
        val accuracyMetres: Double,
    ) : CaptureFix
}

/** What Save did. */
sealed interface WaypointCaptureEvent {
    data object Saved : WaypointCaptureEvent

    /** Nothing was written, for [reason]. */
    data class NotSaved(
        val reason: NotSavedReason,
    ) : WaypointCaptureEvent
}

enum class NotSavedReason {
    /** No live fix yet. */
    NoFix,

    /** Some answers need attention; the form now shows which. */
    FormIncomplete,

    /** Room refused the write. */
    Storage,
}

internal fun DevicePosition?.toCaptureFix(): CaptureFix = if (this == null || !isLive) {
    CaptureFix.Waiting
} else {
    CaptureFix.Located(position.latitude, position.longitude, accuracyMetres)
}
