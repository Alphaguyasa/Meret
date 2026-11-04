package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormAnswersJson
import et.alpha.meret.core.model.form.FormField
import et.alpha.meret.core.model.form.FormRules
import et.alpha.meret.core.model.geo.DevicePosition

/** What the worker typed and picked on the waypoint sheet. */
public data class WaypointInput(
    public val title: String = "",
    public val category: WaypointCategory? = null,
    public val notes: String = "",
    /** The form shown on the sheet, or null when there is none. */
    public val form: WaypointForm? = null,
    /** The form's answers by field key. An unanswered field has no entry. */
    public val answers: Map<String, FormAnswer> = emptyMap(),
)

/**
 * Saves a waypoint where the phone is now.
 *
 * Only a live fix will do. With no fix, or only the platform's cached one (which can be hours
 * old and kilometres away), the save is refused with [AppError.Gps.Kind.NoFix] and nothing is
 * written: a waypoint at the wrong place, or at 0,0, is worse than none.
 *
 * With a form, the answers must pass [FormRules.validate] or the save is refused with
 * [AppError.Invalid] naming the fields. What is stored is [FormRules.answersToKeep]: only the
 * answers of fields the form hid are dropped.
 *
 * Photo answers hold stored photo ids. Each kept one is read back ([PhotoStore.describe]) and
 * saved as a photo row with the waypoint, in one transaction. A photo whose file is gone (it
 * cannot come back) is dropped from the answer rather than refusing the save: the rest of
 * the record is worth more than a reference to nothing. Once the waypoint is in Room, the
 * files of photos taken for fields the form then hid are deleted; no row will ever name them.
 */
public class SaveWaypointUseCase(
    private val store: WaypointStore,
    private val photos: PhotoStore,
) {
    /**
     * [fix] is the phone's position now; [recordingTrackId] the track being recorded, or null
     * while idle. The new waypoint's id once it is in Room.
     */
    public suspend operator fun invoke(
        fix: DevicePosition?,
        recordingTrackId: String?,
        input: WaypointInput,
    ): AppResult<String> {
        val live = fix?.takeIf { it.isLive }
        val invalid =
            input.form
                ?.let { FormRules.validate(it.schema, input.answers).keys.toSet() }
                .orEmpty()
        return when {
            live == null -> AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix))
            invalid.isNotEmpty() -> AppResult.Failure(AppError.Invalid(invalid))
            else -> save(live, recordingTrackId, input)
        }
    }

    private suspend fun save(
        fix: DevicePosition,
        recordingTrackId: String?,
        input: WaypointInput,
    ): AppResult<String> {
        val form = input.form ?: return store.save(newWaypoint(fix, recordingTrackId, input, null))
        val photoKeys =
            form.schema.fields
                .filterIsInstance<FormField.Photo>()
                .map { it.key }
                .toSet()
        val kept = FormRules.answersToKeep(form.schema, input.answers)
        val keptIds = photoIds(kept, photoKeys)
        val stored = keptIds.mapNotNull { photos.describe(it) }
        val found = stored.map { it.id }.toSet()
        val answers =
            kept
                .mapValues { (key, answer) ->
                    if (key in photoKeys && answer is FormAnswer.Photos) {
                        FormAnswer.Photos(answer.photoIds.distinct().filter { it in found })
                    } else {
                        answer
                    }
                }.filterValues { !(it is FormAnswer.Photos && it.photoIds.isEmpty()) }
        val result =
            store.save(
                newWaypoint(fix, recordingTrackId, input, answers).copy(photos = stored),
            )
        if (result is AppResult.Success) {
            (photoIds(input.answers, photoKeys) - keptIds.toSet()).forEach { photos.delete(it) }
        }
        return result
    }

    /** The photo ids [answers] give the fields in [photoKeys], each once, in answer order. */
    private fun photoIds(
        answers: Map<String, FormAnswer>,
        photoKeys: Set<String>,
    ): List<String> = answers
        .filterKeys { it in photoKeys }
        .values
        .filterIsInstance<FormAnswer.Photos>()
        .flatMap { it.photoIds }
        .distinct()

    /** [answers] are those to store, or null when the waypoint has no form. */
    private fun newWaypoint(
        fix: DevicePosition,
        recordingTrackId: String?,
        input: WaypointInput,
        answers: Map<String, FormAnswer>?,
    ): NewWaypoint {
        val form = input.form
        return NewWaypoint(
            position = fix.position,
            accuracyMetres = fix.accuracyMetres,
            altitudeMetres = fix.altitudeMetres,
            trackId = recordingTrackId,
            category = input.category,
            title = input.title.trim().ifEmpty { null },
            notes = input.notes.trim().ifEmpty { null },
            formTemplateId = form?.templateId,
            formVersion = form?.version,
            formData = answers?.let(FormAnswersJson::encode),
        )
    }
}
