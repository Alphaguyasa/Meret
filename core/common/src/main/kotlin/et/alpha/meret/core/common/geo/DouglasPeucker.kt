package et.alpha.meret.core.common.geo

import et.alpha.meret.core.model.geo.LatLng
import kotlin.math.cos

/**
 * Ramer–Douglas–Peucker line simplification with a threshold in metres.
 *
 * Used to turn a recorded track into something a map can draw. A two-hour walk is tens of
 * thousands of fixes; at ε = 3 m it becomes a few hundred points that are visually
 * identical at any zoom a phone can render, and the `GeoJsonSource` update stops dropping
 * frames.
 *
 * Runs on track completion and on demand — never on the fix path.
 */
public object DouglasPeucker {
    /**
     * Returns the subset of [points] whose polyline stays within [epsilonMetres] of the
     * original. First and last points are always kept, and ordering is preserved.
     *
     * Distances are measured on a local equirectangular projection centred on the first
     * point: metres east and metres north. Over a single track the projection's scale
     * error is a few parts in ten thousand, which is three orders of magnitude below the
     * threshold being tested against.
     *
     * The recursion is an explicit stack. A track that is nearly straight recurses to
     * depth O(n), and n here is routinely 50,000 — enough to overflow the JVM stack on
     * the naive form.
     */
    public fun simplify(
        points: List<LatLng>,
        epsilonMetres: Double,
    ): List<LatLng> {
        if (points.size <= 2 || epsilonMetres <= 0.0) return points

        val count = points.size
        val originLatRad = Math.toRadians(points[0].latitude)
        val metresPerDegreeLat = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_METRES
        val metresPerDegreeLon = metresPerDegreeLat * cos(originLatRad)

        val xs = DoubleArray(count)
        val ys = DoubleArray(count)
        for (index in 0 until count) {
            xs[index] = (points[index].longitude - points[0].longitude) * metresPerDegreeLon
            ys[index] = (points[index].latitude - points[0].latitude) * metresPerDegreeLat
        }

        val keep = BooleanArray(count)
        keep[0] = true
        keep[count - 1] = true

        val epsilonSquared = epsilonMetres * epsilonMetres
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        stack.addLast(count - 1)

        while (stack.isNotEmpty()) {
            val last = stack.removeLast()
            val first = stack.removeLast()
            if (last - first < 2) continue

            var worstIndex = -1
            var worstSquared = 0.0
            for (index in first + 1 until last) {
                val distanceSquared = perpendicularDistanceSquared(xs, ys, index, first, last)
                if (distanceSquared > worstSquared) {
                    worstSquared = distanceSquared
                    worstIndex = index
                }
            }

            if (worstIndex >= 0 && worstSquared > epsilonSquared) {
                keep[worstIndex] = true
                stack.addLast(first)
                stack.addLast(worstIndex)
                stack.addLast(worstIndex)
                stack.addLast(last)
            }
        }

        return buildList(count) {
            for (index in 0 until count) {
                if (keep[index]) add(points[index])
            }
        }
    }

    /**
     * Squared distance from a point to the *segment* — not the infinite line — in the
     * projected plane. Squared, because the comparison against ε is all the caller needs
     * and a square root per candidate point is measurable at this call volume.
     */
    private fun perpendicularDistanceSquared(
        xs: DoubleArray,
        ys: DoubleArray,
        index: Int,
        first: Int,
        last: Int,
    ): Double {
        val pointX = xs[index]
        val pointY = ys[index]
        val startX = xs[first]
        val startY = ys[first]

        val segmentX = xs[last] - startX
        val segmentY = ys[last] - startY
        val lengthSquared = segmentX * segmentX + segmentY * segmentY

        val projection =
            if (lengthSquared == 0.0) {
                // Degenerate segment: the endpoints coincide, so fall back to point distance.
                0.0
            } else {
                (((pointX - startX) * segmentX) + ((pointY - startY) * segmentY)) / lengthSquared
            }

        val clamped = projection.coerceIn(0.0, 1.0)
        val nearestX = startX + clamped * segmentX
        val nearestY = startY + clamped * segmentY
        val deltaX = pointX - nearestX
        val deltaY = pointY - nearestY

        return deltaX * deltaX + deltaY * deltaY
    }
}
