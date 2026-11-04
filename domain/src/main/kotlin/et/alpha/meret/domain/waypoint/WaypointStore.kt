package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.LatLng

/** What a worker saves at a point. Text is already trimmed; blank text is null. */
public data class NewWaypoint(
    public val position: LatLng,
    public val accuracyMetres: Double,
    public val altitudeMetres: Double?,
    /** The track being recorded when the waypoint was saved, if any. */
    public val trackId: String?,
    public val category: WaypointCategory?,
    public val title: String?,
    public val notes: String?,
    /** The form the answers were given under, or null for a waypoint saved without one. */
    public val formTemplateId: String? = null,
    public val formVersion: Int? = null,
    /** The answers as `FormAnswersJson` text, hidden fields' answers already dropped. */
    public val formData: String? = null,
    /**
     * The photos the kept answers name, each to be a `waypoint_photo` row of this waypoint,
     * written in the same transaction: a waypoint is never in Room without its photos.
     */
    public val photos: List<StoredPhoto> = emptyList(),
)

/** Writes waypoints. Room, through `:core:data`. */
public interface WaypointStore {
    /**
     * Writes [waypoint] as a new row waiting to be synced, and its photos, in one transaction,
     * and returns its id (a UUIDv7). Nothing here touches the network.
     */
    public suspend fun save(waypoint: NewWaypoint): AppResult<String>
}
