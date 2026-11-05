package et.alpha.meret.feature.map

import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng

/**
 * Something another tab asked the map to show. Either way the map stops following the
 * user, so walking does not pull it away; the recentre button follows again.
 */
sealed interface MapFocus {
    /** A place, such as a waypoint picked in the Waypoints tab: centred at street level. */
    data class Place(
        val position: LatLng,
    ) : MapFocus

    /**
     * A track opened from its detail screen: the map fits [bounds], and draws the track even
     * where more than the usual number of earlier tracks crowd the area.
     */
    data class Track(
        val trackId: String,
        val bounds: BoundingBox,
    ) : MapFocus
}
