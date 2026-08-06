package et.alpha.meret.core.common.fixture

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.geo.LatLng
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Synthetic traces with known ground truth.
 *
 * Generated rather than recorded, and generated deterministically: every fixture is a
 * pure function of its arguments and its seed, so a failing assertion means the code
 * changed, not that a different stretch of a GPX file got sampled.
 *
 * Real recorded traces arrive in `:core:location` at phase 4, where the GPX parser
 * exists to read them. These cover the geometry.
 */
internal object TraceFixtures {
    /** Meskel Square, Addis Ababa. Everything is built relative to somewhere real. */
    val ADDIS_ABABA: LatLng = LatLng(latitude = 9.0107, longitude = 38.7613)

    /**
     * A dead-straight walk: [pointCount] fixes spaced exactly [spacingMetres] apart on a
     * constant heading. Total path length is exactly `(pointCount - 1) * spacingMetres`.
     */
    fun straightWalk(
        pointCount: Int,
        spacingMetres: Double = 1.0,
        bearingDegrees: Double = 45.0,
        start: LatLng = ADDIS_ABABA,
    ): List<LatLng> {
        val points = ArrayList<LatLng>(pointCount)
        var current = start
        points.add(current)
        repeat(pointCount - 1) {
            current = GeoMath.destinationPoint(current, spacingMetres, bearingDegrees)
            points.add(current)
        }
        return points
    }

    /** A 1 km walk sampled once per metre. Known length: 1000 m. */
    fun oneKilometreWalk(): List<LatLng> = straightWalk(pointCount = 1001, spacingMetres = 1.0)

    /**
     * A plausible field trace: a gently winding path, the sort a person walking a field
     * boundary or following a track actually produces. Heading sweeps smoothly rather
     * than randomly, because a random walk is not what simplification has to cope with.
     */
    fun windingWalk(
        pointCount: Int,
        spacingMetres: Double = 1.0,
        start: LatLng = ADDIS_ABABA,
    ): List<LatLng> {
        val points = ArrayList<LatLng>(pointCount)
        var current = start
        points.add(current)
        for (index in 1 until pointCount) {
            val bearing =
                BASE_BEARING_DEGREES +
                    HEADING_SWING_DEGREES * sin(index / HEADING_PERIOD_POINTS) +
                    SECONDARY_SWING_DEGREES * sin(index / SECONDARY_PERIOD_POINTS)
            current = GeoMath.destinationPoint(current, spacingMetres, bearing)
            points.add(current)
        }
        return points
    }

    /**
     * Adds independent Gaussian noise of [sigmaMetres] to each coordinate.
     *
     * Independent per-axis noise is a simplification — real GPS error is correlated over
     * seconds — but it is the harder case for a smoothing filter, not the easier one.
     */
    fun withGaussianNoise(
        trace: List<LatLng>,
        sigmaMetres: Double,
        seed: Long,
    ): List<LatLng> {
        val random = Random(seed)
        return trace.map { point ->
            val metresPerDegreeLat = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_METRES
            val metresPerDegreeLon = metresPerDegreeLat * cos(Math.toRadians(point.latitude))
            LatLng(
                latitude =
                    point.latitude + random.nextGaussian() * sigmaMetres /
                        metresPerDegreeLat,
                longitude =
                    point.longitude + random.nextGaussian() * sigmaMetres /
                        metresPerDegreeLon,
            )
        }
    }

    /**
     * Replaces the point at each index in [indices] with one displaced [metres] away —
     * the multipath spike that a validity gate has to catch.
     */
    fun withOutliers(
        trace: List<LatLng>,
        indices: Set<Int>,
        metres: Double,
    ): List<LatLng> = trace.mapIndexed { index, point ->
        if (index in indices) {
            GeoMath.destinationPoint(point, metres, (index * OUTLIER_BEARING_STEP) % 360.0)
        } else {
            point
        }
    }

    /** A device sitting still, jittering only from noise. */
    fun stationary(
        pointCount: Int,
        sigmaMetres: Double,
        seed: Long,
    ): List<LatLng> = withGaussianNoise(List(pointCount) { ADDIS_ABABA }, sigmaMetres, seed)

    private const val BASE_BEARING_DEGREES = 70.0
    private const val HEADING_SWING_DEGREES = 30.0
    private const val HEADING_PERIOD_POINTS = 50.0
    private const val SECONDARY_SWING_DEGREES = 12.0
    private const val SECONDARY_PERIOD_POINTS = 11.0
    private const val OUTLIER_BEARING_STEP = 47.0
}

/**
 * Perpendicular distance in metres from [point] to the infinite line through
 * [lineStart] and [lineEnd].
 *
 * Projects onto a local plane centred on [point] rather than on the line, so it does not
 * reuse the projection choice made inside `DouglasPeucker` — a bug in that choice would
 * show up here rather than cancelling out.
 */
internal fun crossTrackMetres(
    point: LatLng,
    lineStart: LatLng,
    lineEnd: LatLng,
): Double {
    // The point sits at the origin of its own tangent plane, so its coordinates are (0, 0).
    val (ax, ay) = lineStart.projectedFrom(point)
    val (bx, by) = lineEnd.projectedFrom(point)

    val segmentX = bx - ax
    val segmentY = by - ay
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    if (lengthSquared == 0.0) return hypot(ax, ay)

    val cross = segmentX * -ay - segmentY * -ax
    return abs(cross) / sqrt(lengthSquared)
}

/** Greatest distance from any point of [points] to the polyline [polyline], in metres. */
internal fun maxDeviationMetres(
    points: List<LatLng>,
    polyline: List<LatLng>,
): Double {
    require(polyline.size >= 2) { "a polyline needs at least two points" }
    var worst = 0.0
    for (point in points) {
        var nearest = Double.MAX_VALUE
        for (index in 1 until polyline.size) {
            nearest =
                minOf(
                    nearest,
                    distanceToSegmentMetres(point, polyline[index - 1], polyline[index]),
                )
        }
        if (nearest > worst) worst = nearest
    }
    return worst
}

private fun distanceToSegmentMetres(
    point: LatLng,
    start: LatLng,
    end: LatLng,
): Double {
    // The point sits at the origin of its own tangent plane, so its coordinates are (0, 0).
    val (ax, ay) = start.projectedFrom(point)
    val (bx, by) = end.projectedFrom(point)

    val segmentX = bx - ax
    val segmentY = by - ay
    val lengthSquared = segmentX * segmentX + segmentY * segmentY
    val t =
        if (lengthSquared == 0.0) {
            0.0
        } else {
            ((-ax * segmentX) + (-ay * segmentY)) / lengthSquared
        }.coerceIn(0.0, 1.0)

    return hypot(ax + t * segmentX, ay + t * segmentY)
}

/** Metres east and north of [origin], on a plane tangent at [origin]. */
private fun LatLng.projectedFrom(origin: LatLng): Pair<Double, Double> {
    val metresPerDegreeLat = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_METRES
    val metresPerDegreeLon = metresPerDegreeLat * cos(Math.toRadians(origin.latitude))
    return Pair(
        (longitude - origin.longitude) * metresPerDegreeLon,
        (latitude - origin.latitude) * metresPerDegreeLat,
    )
}
