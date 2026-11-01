package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.DevicePosition

/** What the worker typed and picked on the waypoint sheet. */
public data class WaypointInput(
    public val title: String = "",
    public val category: WaypointCategory? = null,
    public val notes: String = "",
)

/**
 * Saves a waypoint where the phone is now.
 *
 * Only a live fix will do. With no fix, or only the platform's cached one (which can be hours
 * old and kilometres away), the save is refused with [AppError.Gps.Kind.NoFix] and nothing is
 * written: a waypoint at the wrong place, or at 0,0, is worse than none.
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
        if (fix == null || !fix.isLive) {
            return AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix))
        }
        return store.save(
            NewWaypoint(
                position = fix.position,
                accuracyMetres = fix.accuracyMetres,
                altitudeMetres = fix.altitudeMetres,
                trackId = recordingTrackId,
                category = input.category,
                title = input.title.trim().ifEmpty { null },
                notes = input.notes.trim().ifEmpty { null },
            ),
        )
    }
}
