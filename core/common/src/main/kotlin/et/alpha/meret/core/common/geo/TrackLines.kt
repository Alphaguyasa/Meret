package et.alpha.meret.core.common.geo

import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine

/** Builds the [TrackLine] the map draws from a track's points. */
public object TrackLines {
    /**
     * Visually identical to the raw line at any zoom a phone draws (see [DouglasPeucker]), and
     * small enough to hand the map every two seconds during a two-hour walk.
     */
    public const val EPSILON_METRES: Double = 3.0

    /**
     * Splits [points], which come in segment order, into one polyline per segment index and
     * simplifies each. A segment of one point stays: it becomes a line at the next fix.
     */
    public fun build(
        points: List<SegmentPoint>,
        epsilonMetres: Double = EPSILON_METRES,
    ): TrackLine {
        val segments = mutableListOf<List<LatLng>>()
        var current = mutableListOf<LatLng>()
        var currentIndex: Int? = null
        for (point in points) {
            if (point.segmentIndex != currentIndex && current.isNotEmpty()) {
                segments += DouglasPeucker.simplify(current, epsilonMetres)
                current = mutableListOf()
            }
            currentIndex = point.segmentIndex
            current += point.position
        }
        if (current.isNotEmpty()) segments += DouglasPeucker.simplify(current, epsilonMetres)
        return TrackLine(segments)
    }

    /** One point of the line and the segment it belongs to. */
    public data class SegmentPoint(
        public val segmentIndex: Int,
        public val position: LatLng,
    )
}
