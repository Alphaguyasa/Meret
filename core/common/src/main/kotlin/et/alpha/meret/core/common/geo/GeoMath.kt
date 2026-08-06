package et.alpha.meret.core.common.geo

import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Spherical-earth geodesy.
 *
 * A sphere, not an ellipsoid. Over the distances this app measures — a walked plot, a
 * day's track — the spherical approximation is off by well under the GPS error it is
 * being applied to, and it avoids Vincenty's convergence failures on near-antipodal
 * inputs. Where a figure has to be defensible against a GIS reference, the error budget
 * is documented on the function.
 */
public object GeoMath {
    /** IUGG mean earth radius, metres. The radius that makes sphere and ellipsoid agree in area. */
    public const val EARTH_RADIUS_METRES: Double = 6_371_008.8

    /**
     * Great-circle distance in metres.
     *
     * Haversine rather than the spherical law of cosines: the latter loses precision to
     * floating-point cancellation at short distances, and short distances are the only
     * kind this app measures in bulk.
     */
    public fun haversineDistance(
        from: LatLng,
        to: LatLng,
    ): Double {
        val fromLatRad = Math.toRadians(from.latitude)
        val toLatRad = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val sinHalfLat = sin(deltaLat / 2.0)
        val sinHalfLon = sin(deltaLon / 2.0)

        val a =
            sinHalfLat * sinHalfLat +
                cos(fromLatRad) * cos(toLatRad) * sinHalfLon * sinHalfLon

        return 2.0 * EARTH_RADIUS_METRES * asin(sqrt(a).coerceAtMost(1.0))
    }

    /** Sum of the great-circle distances between consecutive points, in metres. */
    public fun pathLength(points: List<LatLng>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (index in 1 until points.size) {
            total += haversineDistance(points[index - 1], points[index])
        }
        return total
    }

    /**
     * Initial great-circle bearing from [from] to [to], in degrees clockwise from true
     * north, normalised to `[0, 360)`.
     *
     * "Initial" matters: a great-circle course changes continuously, so this is the
     * heading at the start of the leg, not an average.
     */
    public fun initialBearing(
        from: LatLng,
        to: LatLng,
    ): Double {
        val fromLatRad = Math.toRadians(from.latitude)
        val toLatRad = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(deltaLon) * cos(toLatRad)
        val x =
            cos(fromLatRad) * sin(toLatRad) -
                sin(fromLatRad) * cos(toLatRad) * cos(deltaLon)

        return normaliseDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** The point reached by travelling [distanceMetres] from [from] on [bearingDegrees]. */
    public fun destinationPoint(
        from: LatLng,
        distanceMetres: Double,
        bearingDegrees: Double,
    ): LatLng {
        val angularDistance = distanceMetres / EARTH_RADIUS_METRES
        val bearingRad = Math.toRadians(bearingDegrees)
        val fromLatRad = Math.toRadians(from.latitude)
        val fromLonRad = Math.toRadians(from.longitude)

        val sinLat =
            sin(fromLatRad) * cos(angularDistance) +
                cos(fromLatRad) * sin(angularDistance) * cos(bearingRad)
        val latRad = asin(sinLat.coerceIn(-1.0, 1.0))

        val lonRad =
            fromLonRad +
                atan2(
                    sin(bearingRad) * sin(angularDistance) * cos(fromLatRad),
                    cos(angularDistance) - sin(fromLatRad) * sinLat,
                )

        return LatLng(
            latitude = Math.toDegrees(latRad),
            longitude = normaliseLongitude(Math.toDegrees(lonRad)),
        )
    }

    /**
     * Geodesic area of a polygon in square metres, always positive.
     *
     * The polygon is treated as closed; a repeated final vertex is harmless. Fewer than
     * three vertices is zero area, as is any degenerate or collinear ring.
     *
     * Accuracy: exact for a spherical polygon with great-circle edges. Against a GIS
     * reference computed on the WGS84 ellipsoid the difference is under 0.1% for the
     * plot- and district-sized polygons this app measures. Note that edges are great
     * circles, not rhumb lines — a "rectangle" whose north and south sides follow
     * parallels of latitude encloses slightly less than this returns, because a great
     * circle between two points at equal latitude bulges poleward.
     */
    public fun geodesicPolygonArea(vertices: List<LatLng>): Double =
        abs(signedPolygonArea(vertices))

    /**
     * Signed geodesic area: positive when the ring winds counter-clockwise as seen from
     * outside the sphere, negative when clockwise. Used for winding-order checks.
     *
     * Implemented by summing the areas of the polar triangles subtended by each edge.
     * Longitude enters only through the *difference* between consecutive vertices, fed
     * straight into `sin`/`cos`, so a ring crossing the antimeridian needs no special
     * casing and no unwrapping pass.
     */
    public fun signedPolygonArea(vertices: List<LatLng>): Double {
        if (vertices.size < MIN_POLYGON_VERTICES) return 0.0

        var total = 0.0
        val previous = vertices.last()
        var previousTangent = tan((HALF_PI - Math.toRadians(previous.latitude)) / 2.0)
        var previousLonRad = Math.toRadians(previous.longitude)

        for (vertex in vertices) {
            val tangent = tan((HALF_PI - Math.toRadians(vertex.latitude)) / 2.0)
            val lonRad = Math.toRadians(vertex.longitude)
            total += polarTriangleArea(tangent, lonRad, previousTangent, previousLonRad)
            previousTangent = tangent
            previousLonRad = lonRad
        }

        return total * EARTH_RADIUS_METRES * EARTH_RADIUS_METRES
    }

    /** Perimeter of a polygon in metres, including the closing edge. */
    public fun polygonPerimeter(vertices: List<LatLng>): Double {
        if (vertices.size < 2) return 0.0
        return pathLength(vertices) + haversineDistance(vertices.last(), vertices.first())
    }

    /**
     * Ray-casting containment test. Vertices on or exactly at an edge are undefined —
     * a GPS fix never lands exactly on a boundary, and paying for the epsilon comparison
     * on every cell of a coverage grid is not worth it.
     *
     * Longitudes are taken relative to [point] and wrapped into `[-180, 180)` before the
     * crossing test, so a polygon spanning the antimeridian is handled correctly. Assumes
     * no single edge spans more than 180° of longitude, which no sane ring does.
     */
    public fun pointInPolygon(
        point: LatLng,
        polygon: List<LatLng>,
    ): Boolean {
        if (polygon.size < MIN_POLYGON_VERTICES) return false

        var inside = false
        var previous = polygon.last()
        var previousX = normaliseLongitude(previous.longitude - point.longitude)

        for (vertex in polygon) {
            val currentX = normaliseLongitude(vertex.longitude - point.longitude)
            val crossesLatitude =
                (vertex.latitude > point.latitude) != (previous.latitude > point.latitude)

            if (crossesLatitude) {
                val t =
                    (point.latitude - vertex.latitude) /
                        (previous.latitude - vertex.latitude)
                // Longitude where the edge crosses the test point's parallel. The ray is
                // cast eastward from the point, which sits at x = 0 by construction.
                if (currentX + t * (previousX - currentX) > 0.0) {
                    inside = !inside
                }
            }

            previous = vertex
            previousX = currentX
        }

        return inside
    }

    /** The smallest [BoundingBox] containing every point, or `null` for an empty list. */
    public fun boundingBox(points: List<LatLng>): BoundingBox? {
        if (points.isEmpty()) return null

        var minLat = Double.POSITIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY

        for (point in points) {
            if (point.latitude < minLat) minLat = point.latitude
            if (point.latitude > maxLat) maxLat = point.latitude
            if (point.longitude < minLon) minLon = point.longitude
            if (point.longitude > maxLon) maxLon = point.longitude
        }

        return BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    /**
     * Grows a box by [metres] on every side.
     *
     * The longitude expansion uses the cosine at whichever edge is nearer a pole, so the
     * result is never narrower than requested anywhere along its height. Latitude is
     * clamped to the poles; longitude is clamped to a full sweep rather than wrapped,
     * since [BoundingBox] does not model wrapping.
     */
    public fun expandBbox(
        box: BoundingBox,
        metres: Double,
    ): BoundingBox {
        val latDelta = Math.toDegrees(metres / EARTH_RADIUS_METRES)

        val widestLatitude = max(abs(box.minLatitude), abs(box.maxLatitude))
        val cosLat = cos(Math.toRadians(widestLatitude)).coerceAtLeast(MIN_COS_LATITUDE)
        val lonDelta = Math.toDegrees(metres / (EARTH_RADIUS_METRES * cosLat))

        return BoundingBox(
            minLatitude = (box.minLatitude - latDelta).coerceAtLeast(LatLng.MIN_LATITUDE),
            minLongitude = (box.minLongitude - lonDelta).coerceAtLeast(LatLng.MIN_LONGITUDE),
            maxLatitude = (box.maxLatitude + latDelta).coerceAtMost(LatLng.MAX_LATITUDE),
            maxLongitude = (box.maxLongitude + lonDelta).coerceAtMost(LatLng.MAX_LONGITUDE),
        )
    }

    /** Normalises degrees into `[0, 360)`. */
    public fun normaliseDegrees(degrees: Double): Double =
        ((degrees % FULL_CIRCLE) + FULL_CIRCLE) % FULL_CIRCLE

    /** Normalises a longitude into `[-180, 180)`. Exactly 180° maps to -180°. */
    public fun normaliseLongitude(degrees: Double): Double {
        val wrapped = ((degrees + HALF_CIRCLE) % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE
        return wrapped - HALF_CIRCLE
    }

    /**
     * Area of the triangle formed by an edge and the north pole, in steradians.
     *
     * Arguments are `tan((π/2 − φ)/2)` for each endpoint — the stereographic projection
     * of the co-latitude — which is what lets the whole thing reduce to one `atan2`.
     */
    private fun polarTriangleArea(
        tangent1: Double,
        lon1: Double,
        tangent2: Double,
        lon2: Double,
    ): Double {
        val deltaLon = lon1 - lon2
        val product = tangent1 * tangent2
        return 2.0 * atan2(product * sin(deltaLon), 1.0 + product * cos(deltaLon))
    }

    private const val MIN_POLYGON_VERTICES = 3
    private const val HALF_PI = Math.PI / 2.0
    private const val HALF_CIRCLE = 180.0
    private const val FULL_CIRCLE = 360.0

    /** Guards the longitude expansion against dividing by zero at the poles. */
    private const val MIN_COS_LATITUDE = 1e-6
}
