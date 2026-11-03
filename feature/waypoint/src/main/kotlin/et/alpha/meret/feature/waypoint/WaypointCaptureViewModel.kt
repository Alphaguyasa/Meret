package et.alpha.meret.feature.waypoint

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
import et.alpha.meret.domain.waypoint.WaypointDraft
import et.alpha.meret.domain.waypoint.WaypointDraftStore
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * The text, category and answers are a draft in Room ([WaypointDraftStore]), written on every
 * change, so a killed app (swiped away, out of memory, a flat battery) reopens the sheet with
 * all of them. A write in progress coalesces the changes made meanwhile into the next one, so
 * fast typing is a handful of small writes, never a queue. A dismissed sheet keeps its draft,
 * for a swipe made by mistake; a saved one starts empty and the draft is deleted.
 */
@HiltViewModel
class WaypointCaptureViewModel
    @Inject
    constructor(
        devicePosition: DevicePositionRepository,
        private val tracking: TrackingController,
        private val saveWaypoint: SaveWaypointUseCase,
        private val drafts: WaypointDraftStore,
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

        private val input = MutableStateFlow(WaypointInput())

        private val draft = MutableStateFlow(FormDraft())

        /** One draft write at a time, so an older state never lands after a newer one. */
        private val draftWrites = Mutex()

        init {
            viewModelScope.launch {
                val saved = drafts.load()
                restore(saved, formSource.currentForm())
                // From here on, every change is written. Not before: the empty sheet of a
                // new ViewModel must not overwrite the draft it is about to restore.
                merge(input, draft).conflate().collect { persistDraft() }
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
            input.update { it.copy(title = title) }
        }

        fun onNotesChange(notes: String) {
            input.update { it.copy(notes = notes) }
        }

        /** Picks [category], or clears it when it is the one already picked. */
        fun onCategoryTap(category: WaypointCategory) {
            val picked = category.takeUnless { it == input.value.category }
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
            draft.value = current.copy(answers = answers)
        }

        /** What is in a number field's box, as typed: it may not be a number (yet). */
        fun onNumberTextChange(
            key: String,
            text: String,
        ) {
            val current = draft.value
            if (current.form?.schema?.field(key) !is FormField.Number) return
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
                                persistDraft()
                                WaypointCaptureEvent.Saved
                            }

                            is AppResult.Failure -> {
                                WaypointCaptureEvent.NotSaved(result.error.toReason())
                            }
                        }
                    if (event == WaypointCaptureEvent.NotSaved(NotSavedReason.FormIncomplete)) {
                        draft.update { it.copy(showErrors = true) }
                    }
                    mutableEvents.send(event)
                }
        }

        private fun clearInput() {
            input.value = WaypointInput()
            draft.update { FormDraft(form = it.form, loaded = it.loaded) }
        }

        /**
         * The sheet as the last ViewModel left it in Room. Anything typed in the moment before
         * the draft was read is kept over the draft. Answers given under another template are
         * not restored: the same key may ask something else there.
         */
        private fun restore(
            saved: WaypointDraft?,
            form: WaypointForm?,
        ) {
            if (saved != null) {
                input.update { typed ->
                    typed.copy(
                        title = typed.title.ifEmpty { saved.title },
                        category = typed.category ?: WaypointCategory.fromKey(saved.categoryKey),
                        notes = typed.notes.ifEmpty { saved.notes },
                    )
                }
            }
            draft.value =
                when {
                    form == null -> {
                        FormDraft(loaded = true)
                    }

                    saved != null && saved.formTemplateId == form.templateId -> {
                        restoreAnswers(form, saved)
                    }

                    else -> {
                        FormDraft(form, loaded = true)
                    }
                }
        }

        private fun restoreAnswers(
            form: WaypointForm,
            saved: WaypointDraft,
        ): FormDraft {
            val decoded = FormAnswersJson.decode(saved.answersJson, form.schema)
            val answers = (decoded as? FormAnswersJson.Decoded.Answers)?.answers.orEmpty()
            val numberKeys =
                form.schema.fields
                    .filterIsInstance<FormField.Number>()
                    .map { it.key }
                    .toSet()
            return FormDraft(
                form = form,
                loaded = true,
                answers = answers,
                numberTexts = saved.numberTexts.filterKeys { it in numberKeys },
                showErrors = saved.showErrors,
            )
        }

        /** Writes the sheet as it is now: the latest state, whichever change asked for it. */
        private suspend fun persistDraft() {
            draftWrites.withLock {
                val now = currentDraft()
                // A failed draft write keeps the sheet as it is; the next change tries again.
                if (now.isEmpty) drafts.clear() else drafts.save(now)
            }
        }

        private fun currentDraft(): WaypointDraft {
            val typed = input.value
            val form = draft.value
            return WaypointDraft(
                title = typed.title,
                categoryKey = typed.category?.key,
                notes = typed.notes,
                formTemplateId = form.form?.templateId,
                formVersion = form.form?.version,
                answersJson = form.answers.takeIf { it.isNotEmpty() }?.let(FormAnswersJson::encode),
                numberTexts = form.numberTexts.filterValues { it.isNotEmpty() },
                showErrors = form.showErrors,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
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
