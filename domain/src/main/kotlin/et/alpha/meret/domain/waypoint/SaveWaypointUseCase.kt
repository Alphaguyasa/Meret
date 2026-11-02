package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.form.FormAnswer
import et.alpha.meret.core.model.form.FormAnswersJson
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
 */
public class SaveWaypointUseCase(
    private val store: WaypointStore,
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
            else -> store.save(newWaypoint(live, recordingTrackId, input))
        }
    }

    private fun newWaypoint(
        fix: DevicePosition,
        recordingTrackId: String?,
        input: WaypointInput,
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
            formData =
                form?.let {
                    FormAnswersJson.encode(FormRules.answersToKeep(it.schema, input.answers))
                },
        )
    }
}
