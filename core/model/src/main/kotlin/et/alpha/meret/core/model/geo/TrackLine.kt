package et.alpha.meret.core.model.geo

/**
 * A track as the map draws it: one polyline per segment, in order. A pause ends a segment, so
 * the map draws no line across ground the worker did not record.
 *
 * Display geometry only: simplified, outliers dropped. The raw fixes stay in Room.
 */
public data class TrackLine(
    public val segments: List<List<LatLng>>,
)
