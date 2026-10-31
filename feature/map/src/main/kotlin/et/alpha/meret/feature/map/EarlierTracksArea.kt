package et.alpha.meret.feature.map

import et.alpha.meret.core.model.geo.BoundingBox

/**
 * Which ground to load earlier tracks for, given what is on screen.
 *
 * The area loaded is the screen grown by half its size on every side, so a pan of up to half
 * a screen, or a zoom-in of one level, re-uses what is already loaded and asks Room nothing.
 * It is replaced when the screen leaves it (a longer pan, a zoom-out) or when the screen has
 * become small inside it (zoomed in further): an area much larger than the screen would
 * spend the track cap on tracks nobody can see.
 */
internal object EarlierTracksArea {
    /** Half a screen of margin on each side: the loaded area is twice the screen across. */
    private const val MARGIN = 0.5

    /** After this much zoom-in (two levels), the loaded area is too big for the screen. */
    private const val MAX_SPAN_RATIO = 4.0

    private const val MAX_LATITUDE = 85.0
    private const val MAX_LONGITUDE = 180.0

    /** [loaded] when it still serves [visible], otherwise a new area around [visible]. */
    fun next(
        loaded: BoundingBox?,
        visible: BoundingBox,
    ): BoundingBox = if (loaded != null && serves(loaded, visible)) loaded else around(visible)

    private fun serves(
        loaded: BoundingBox,
        visible: BoundingBox,
    ): Boolean {
        val inside =
            visible.minLatitude >= loaded.minLatitude &&
                visible.maxLatitude <= loaded.maxLatitude &&
                visible.minLongitude >= loaded.minLongitude &&
                visible.maxLongitude <= loaded.maxLongitude
        val notTooBig =
            loaded.latitudeSpan <= visible.latitudeSpan * MAX_SPAN_RATIO &&
                loaded.longitudeSpan <= visible.longitudeSpan * MAX_SPAN_RATIO
        return inside && notTooBig
    }

    private fun around(visible: BoundingBox): BoundingBox {
        val latitudeMargin = visible.latitudeSpan * MARGIN
        val longitudeMargin = visible.longitudeSpan * MARGIN
        return BoundingBox(
            minLatitude = (visible.minLatitude - latitudeMargin).coerceAtLeast(-MAX_LATITUDE),
            minLongitude = (visible.minLongitude - longitudeMargin).coerceAtLeast(-MAX_LONGITUDE),
            maxLatitude = (visible.maxLatitude + latitudeMargin).coerceAtMost(MAX_LATITUDE),
            maxLongitude = (visible.maxLongitude + longitudeMargin).coerceAtMost(MAX_LONGITUDE),
        )
    }
}
