package et.alpha.meret.core.model.geo

/**
 * A waypoint as the map draws it: where, and which category for its colour. Nothing the
 * worker typed: the map has no use for it, and a marker list stays small in memory.
 */
public data class WaypointMarker(
    public val id: String,
    public val position: LatLng,
    public val category: String?,
)
