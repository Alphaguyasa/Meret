package et.alpha.meret.feature.waypoint

import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.waypoint.WaypointCategory

/** The Waypoints tab. */
sealed interface WaypointListUiState {
    /** The waypoints have not been read yet. */
    data object Loading : WaypointListUiState

    data class Ready(
        /** Newest first. */
        val waypoints: List<WaypointItem>,
    ) : WaypointListUiState
}

/**
 * One waypoint, as its row shows it. The row's heading is [title], or else the category, or
 * else the time it was saved.
 */
data class WaypointItem(
    val id: String,
    val title: String?,
    /** A category this build knows, with a translated label. */
    val category: WaypointCategory?,
    /**
     * The stored key when this build does not know it (a project's own category, after
     * sync): shown as it is rather than hidden.
     */
    val otherCategoryKey: String?,
    val recordedAtMillis: Long,
    val position: LatLng,
    val syncState: SyncState,
)
