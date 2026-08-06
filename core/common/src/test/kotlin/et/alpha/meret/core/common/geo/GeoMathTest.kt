package et.alpha.meret.core.common.geo

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.fixture.TraceFixtures
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class GeoMathTest {
    // ---- Distance --------------------------------------------------------------------

    @Test
    fun `haversine matches the great-circle distance between Paris and Berlin`() {
        // Published great-circle distance, Paris (48.8566, 2.3522) to Berlin
        // (52.5200, 13.4050): approximately 878 km.
        val distance = GeoMath.haversineDistance(PARIS, BERLIN)

        assertThat(distance).isWithin(PUBLISHED_TOLERANCE * 878_000.0).of(878_000.0)
    }

    @Test
    fun `haversine matches the great-circle distance between Addis Ababa and Nairobi`() {
        // Published great-circle distance: approximately 1166 km.
        val distance = GeoMath.haversineDistance(ADDIS_ABABA, NAIROBI)

        assertThat(distance).isWithin(PUBLISHED_TOLERANCE * 1_166_000.0).of(1_166_000.0)
    }

    @Test
    fun `one degree of latitude is one meridional degree of the sphere`() {
        // Independent of the implementation: R * pi/180, by definition of a radian.
        val expected = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_METRES

        val measured = GeoMath.haversineDistance(LatLng(0.0, 0.0), LatLng(1.0, 0.0))

        assertThat(measured).isWithin(expected * 1e-9).of(expected)
    }

    @Test
    fun `haversine agrees with the spherical law of cosines at continental range`() {
        // A different closed form for the same quantity. Haversine is used in production
        // because the law of cosines loses precision at short range; at 1000 km they must
        // still agree to the last few digits.
        val haversine = GeoMath.haversineDistance(ADDIS_ABABA, NAIROBI)
        val lawOfCosines = sphericalLawOfCosines(ADDIS_ABABA, NAIROBI)

        assertThat(haversine).isWithin(haversine * 1e-9).of(lawOfCosines)
    }

    @Test
    fun `haversine is zero for identical points and symmetric otherwise`() {
        assertThat(GeoMath.haversineDistance(ADDIS_ABABA, ADDIS_ABABA)).isEqualTo(0.0)
        assertThat(GeoMath.haversineDistance(ADDIS_ABABA, NAIROBI))
            .isWithin(1e-6)
            .of(GeoMath.haversineDistance(NAIROBI, ADDIS_ABABA))
    }

    @Test
    fun `haversine handles an antimeridian crossing without wrapping the long way round`() {
        val west = LatLng(0.0, 179.5)
        val east = LatLng(0.0, -179.5)
        val expected = Math.toRadians(1.0) * GeoMath.EARTH_RADIUS_METRES

        assertThat(GeoMath.haversineDistance(west, east)).isWithin(expected * 1e-9).of(expected)
    }

    @Test
    fun `pathLength of a known one kilometre walk is one kilometre`() {
        val walk = TraceFixtures.oneKilometreWalk()

        assertThat(GeoMath.pathLength(walk)).isWithin(0.01).of(1000.0)
    }

    @Test
    fun `pathLength is zero for fewer than two points`() {
        assertThat(GeoMath.pathLength(emptyList())).isEqualTo(0.0)
        assertThat(GeoMath.pathLength(listOf(ADDIS_ABABA))).isEqualTo(0.0)
    }

    // ---- Bearing and projection ------------------------------------------------------

    @Test
    fun `initialBearing reports the cardinal directions`() {
        val origin = LatLng(9.0, 38.0)

        assertThat(GeoMath.initialBearing(origin, LatLng(10.0, 38.0))).isWithin(1e-9).of(0.0)
        assertThat(GeoMath.initialBearing(origin, LatLng(8.0, 38.0))).isWithin(1e-9).of(180.0)

        // Due east/west is only approximate, and correctly so: a great circle leaving on a
        // bearing of exactly 90° does not stay on its parallel. Over 1° of longitude at
        // 9°N the initial bearing sits about 0.08° north of due east.
        assertThat(GeoMath.initialBearing(origin, LatLng(9.0, 39.0))).isWithin(0.1).of(90.0)
        assertThat(GeoMath.initialBearing(origin, LatLng(9.0, 37.0))).isWithin(0.1).of(270.0)
    }

    @Test
    fun `destinationPoint lands exactly the requested distance away on the requested bearing`() {
        val origin = LatLng(9.0, 38.0)
        val bearings = listOf(0.0, 37.0, 90.0, 174.0, 255.0, 349.0)

        for (bearing in bearings) {
            val destination = GeoMath.destinationPoint(origin, 5_000.0, bearing)

            assertThat(GeoMath.haversineDistance(origin, destination)).isWithin(1e-4).of(5_000.0)
            assertThat(GeoMath.initialBearing(origin, destination)).isWithin(1e-6).of(bearing)
        }
    }

    @Test
    fun `destinationPoint wraps longitude across the antimeridian`() {
        val nearTheLine = LatLng(0.0, 179.99)

        val crossed = GeoMath.destinationPoint(nearTheLine, 5_000.0, 90.0)

        assertThat(crossed.longitude).isLessThan(0.0)
        assertThat(crossed.longitude).isGreaterThan(-180.0)
    }

    // ---- Area ------------------------------------------------------------------------

    @Test
    fun `geodesic area of a one kilometre box matches the analytic spherical rectangle`() {
        // At this size the great-circle edges deviate from the parallels by about 3 mm,
        // so the closed form and the polygon algorithm should agree to many digits.
        val south = 9.0
        val north = 9.0 + Math.toDegrees(1_000.0 / GeoMath.EARTH_RADIUS_METRES)
        val west = 38.75
        val east =
            38.75 +
                Math.toDegrees(
                    1_000.0 / (GeoMath.EARTH_RADIUS_METRES * cos(Math.toRadians(south))),
                )
        val expected = analyticSphericalRectangleArea(south, north, west, east)

        val area =
            GeoMath.geodesicPolygonArea(
                listOf(
                    LatLng(south, west),
                    LatLng(south, east),
                    LatLng(north, east),
                    LatLng(north, west),
                ),
            )

        assertThat(relativeError(area, expected)).isLessThan(REFERENCE_TOLERANCE)
        // Sanity: it is also, to within a hair, one square kilometre.
        assertThat(relativeError(area, 1_000_000.0)).isLessThan(0.001)
    }

    @Test
    fun `geodesic area of the Colorado quadrilateral matches the analytic spherical rectangle`() {
        val expected =
            analyticSphericalRectangleArea(
                southLatitude = COLORADO_SOUTH,
                northLatitude = COLORADO_NORTH,
                westLongitude = COLORADO_WEST,
                eastLongitude = COLORADO_EAST,
            )

        val area = GeoMath.geodesicPolygonArea(coloradoQuadrilateral())

        println(
            "Colorado quadrilateral: computed ${"%.0f".format(area / 1e6)} km², " +
                "analytic ${"%.0f".format(expected / 1e6)} km², " +
                "relative error ${"%.4f".format(relativeError(area, expected) * 100)}%",
        )

        // Not identical, and expected not to be: the polygon's north and south edges are
        // great circles, which bulge poleward of the parallels the closed form integrates
        // along. The two bulges nearly cancel, leaving a residual well inside 0.1%.
        assertThat(relativeError(area, expected)).isLessThan(REFERENCE_TOLERANCE)
    }

    @Test
    fun `geodesic area of the Colorado quadrilateral is close to the published state area`() {
        // Colorado is commonly published at 269,601 km². The gap against that figure is
        // real and is not an error in this code: the published number is measured on the
        // WGS84 ellipsoid, and Colorado's surveyed boundary is not exactly the
        // quadrilateral its legal description implies.
        val publishedSquareMetres = 269_601e6

        val area = GeoMath.geodesicPolygonArea(coloradoQuadrilateral())

        assertThat(relativeError(area, publishedSquareMetres)).isLessThan(0.005)
    }

    @Test
    fun `geodesic area is correct for a polygon straddling the antimeridian`() {
        val expected =
            analyticSphericalRectangleArea(
                southLatitude = 0.0,
                northLatitude = 1.0,
                westLongitude = 179.0,
                eastLongitude = 181.0,
            )

        val area =
            GeoMath.geodesicPolygonArea(
                listOf(
                    LatLng(0.0, 179.0),
                    LatLng(0.0, -179.0),
                    LatLng(1.0, -179.0),
                    LatLng(1.0, 179.0),
                ),
            )

        assertThat(relativeError(area, expected)).isLessThan(REFERENCE_TOLERANCE)
    }

    @Test
    fun `geodesic area is zero for degenerate rings`() {
        val point = LatLng(9.0, 38.0)

        assertThat(GeoMath.geodesicPolygonArea(emptyList())).isEqualTo(0.0)
        assertThat(GeoMath.geodesicPolygonArea(listOf(point))).isEqualTo(0.0)
        assertThat(GeoMath.geodesicPolygonArea(listOf(point, LatLng(9.1, 38.1)))).isEqualTo(0.0)
        assertThat(GeoMath.geodesicPolygonArea(List(4) { point })).isWithin(1e-6).of(0.0)
    }

    @Test
    fun `geodesic area is unaffected by duplicate and collinear vertices`() {
        val square =
            listOf(
                LatLng(9.00, 38.00),
                LatLng(9.00, 38.01),
                LatLng(9.01, 38.01),
                LatLng(9.01, 38.00),
            )
        val padded =
            listOf(
                LatLng(9.00, 38.00),
                LatLng(9.00, 38.00), // duplicate
                LatLng(9.00, 38.005), // collinear with the edge it sits on
                LatLng(9.00, 38.01),
                LatLng(9.01, 38.01),
                LatLng(9.01, 38.00),
                LatLng(9.01, 38.00), // duplicate
            )

        // Not bit-identical: splitting an edge at a point on the parallel makes the ring
        // hug the parallel a little more closely than the single great-circle edge did.
        // That is a real geometric difference, and at this scale it is a couple of square
        // metres in twelve hectares.
        val paddedArea = GeoMath.geodesicPolygonArea(padded)
        val squareArea = GeoMath.geodesicPolygonArea(square)

        assertThat(relativeError(paddedArea, squareArea)).isLessThan(1e-5)
    }

    @Test
    fun `geodesic area ignores an explicitly repeated closing vertex`() {
        val open = listOf(LatLng(9.0, 38.0), LatLng(9.0, 38.01), LatLng(9.01, 38.01))
        val closed = open + open.first()

        assertThat(GeoMath.geodesicPolygonArea(closed))
            .isWithin(1e-6)
            .of(GeoMath.geodesicPolygonArea(open))
    }

    @Test
    fun `signedPolygonArea changes sign with winding order`() {
        val counterClockwise =
            listOf(
                LatLng(9.00, 38.00),
                LatLng(9.00, 38.01),
                LatLng(9.01, 38.01),
                LatLng(9.01, 38.00),
            )

        val forward = GeoMath.signedPolygonArea(counterClockwise)
        val reversed = GeoMath.signedPolygonArea(counterClockwise.reversed())

        assertThat(forward).isWithin(abs(forward) * 1e-9).of(-reversed)
        assertThat(forward * reversed).isLessThan(0.0)
    }

    @Test
    fun `polygonPerimeter includes the closing edge`() {
        val triangle = listOf(LatLng(9.0, 38.0), LatLng(9.0, 38.01), LatLng(9.01, 38.01))
        val expected =
            GeoMath.pathLength(triangle) +
                GeoMath.haversineDistance(triangle.last(), triangle.first())

        assertThat(GeoMath.polygonPerimeter(triangle)).isWithin(1e-9).of(expected)
        assertThat(GeoMath.polygonPerimeter(listOf(triangle.first()))).isEqualTo(0.0)
    }

    // ---- Containment -----------------------------------------------------------------

    @Test
    fun `pointInPolygon distinguishes inside from outside`() {
        val square =
            listOf(
                LatLng(9.00, 38.00),
                LatLng(9.00, 38.10),
                LatLng(9.10, 38.10),
                LatLng(9.10, 38.00),
            )

        assertThat(GeoMath.pointInPolygon(LatLng(9.05, 38.05), square)).isTrue()
        assertThat(GeoMath.pointInPolygon(LatLng(9.05, 38.20), square)).isFalse()
        assertThat(GeoMath.pointInPolygon(LatLng(8.90, 38.05), square)).isFalse()
        assertThat(GeoMath.pointInPolygon(LatLng(9.05, 37.90), square)).isFalse()
    }

    @Test
    fun `pointInPolygon handles a concave ring`() {
        // An L shape: the notch must read as outside even though it is inside the bbox.
        val ell =
            listOf(
                LatLng(0.0, 0.0),
                LatLng(0.0, 2.0),
                LatLng(1.0, 2.0),
                LatLng(1.0, 1.0),
                LatLng(2.0, 1.0),
                LatLng(2.0, 0.0),
            )

        assertThat(GeoMath.pointInPolygon(LatLng(0.5, 0.5), ell)).isTrue()
        assertThat(GeoMath.pointInPolygon(LatLng(1.5, 1.5), ell)).isFalse()
    }

    @Test
    fun `pointInPolygon handles a ring straddling the antimeridian`() {
        val straddling =
            listOf(
                LatLng(-1.0, 179.0),
                LatLng(-1.0, -179.0),
                LatLng(1.0, -179.0),
                LatLng(1.0, 179.0),
            )

        assertThat(GeoMath.pointInPolygon(LatLng(0.0, 179.9), straddling)).isTrue()
        assertThat(GeoMath.pointInPolygon(LatLng(0.0, -179.9), straddling)).isTrue()
        assertThat(GeoMath.pointInPolygon(LatLng(0.0, 178.0), straddling)).isFalse()
        assertThat(GeoMath.pointInPolygon(LatLng(0.0, -178.0), straddling)).isFalse()
    }

    @Test
    fun `pointInPolygon rejects a ring with fewer than three vertices`() {
        assertThat(GeoMath.pointInPolygon(ADDIS_ABABA, emptyList())).isFalse()
        assertThat(GeoMath.pointInPolygon(ADDIS_ABABA, listOf(ADDIS_ABABA, NAIROBI))).isFalse()
    }

    // ---- Bounding boxes --------------------------------------------------------------

    @Test
    fun `boundingBox is null for no points and tight for some`() {
        assertThat(GeoMath.boundingBox(emptyList())).isNull()

        val box =
            GeoMath.boundingBox(
                listOf(LatLng(9.0, 38.0), LatLng(8.5, 39.0), LatLng(9.5, 37.5)),
            )

        assertThat(box).isEqualTo(BoundingBox(8.5, 37.5, 9.5, 39.0))
    }

    @Test
    fun `boundingBox of a single point is degenerate but valid`() {
        val box = GeoMath.boundingBox(listOf(ADDIS_ABABA))

        assertThat(box?.latitudeSpan).isEqualTo(0.0)
        assertThat(box?.contains(ADDIS_ABABA)).isTrue()
    }

    @Test
    fun `expandBbox grows every side by at least the requested distance`() {
        val box = BoundingBox(9.0, 38.0, 9.1, 38.1)

        val expanded = GeoMath.expandBbox(box, 500.0)

        val northGrowth =
            GeoMath.haversineDistance(
                LatLng(box.maxLatitude, box.minLongitude),
                LatLng(expanded.maxLatitude, box.minLongitude),
            )
        // Measured along the poleward edge, where the longitude expansion was sized.
        val eastGrowthAtWidest =
            GeoMath.haversineDistance(
                LatLng(box.maxLatitude, box.maxLongitude),
                LatLng(box.maxLatitude, expanded.maxLongitude),
            )
        // Measured along the equatorward edge, where a degree of longitude is longer:
        // the box must be at least as wide there, never narrower than asked for.
        val eastGrowthAtNarrowest =
            GeoMath.haversineDistance(
                LatLng(box.minLatitude, box.maxLongitude),
                LatLng(box.minLatitude, expanded.maxLongitude),
            )

        assertThat(northGrowth).isWithin(0.5).of(500.0)
        assertThat(eastGrowthAtWidest).isWithin(0.5).of(500.0)
        assertThat(eastGrowthAtNarrowest).isAtLeast(eastGrowthAtWidest)
    }

    @Test
    fun `expandBbox clamps at the poles rather than overflowing`() {
        val polar = BoundingBox(-89.999, -179.999, 89.999, 179.999)

        val expanded = GeoMath.expandBbox(polar, 100_000.0)

        assertThat(expanded.minLatitude).isEqualTo(-90.0)
        assertThat(expanded.maxLatitude).isEqualTo(90.0)
        assertThat(expanded.minLongitude).isEqualTo(-180.0)
        assertThat(expanded.maxLongitude).isEqualTo(180.0)
    }

    // ---- Normalisation ---------------------------------------------------------------

    @Test
    fun `normaliseDegrees maps onto zero up to three sixty`() {
        assertThat(GeoMath.normaliseDegrees(0.0)).isEqualTo(0.0)
        assertThat(GeoMath.normaliseDegrees(360.0)).isEqualTo(0.0)
        assertThat(GeoMath.normaliseDegrees(-90.0)).isWithin(1e-9).of(270.0)
        assertThat(GeoMath.normaliseDegrees(725.0)).isWithin(1e-9).of(5.0)
    }

    @Test
    fun `normaliseLongitude maps onto minus one eighty up to one eighty`() {
        assertThat(GeoMath.normaliseLongitude(0.0)).isEqualTo(0.0)
        assertThat(GeoMath.normaliseLongitude(181.0)).isWithin(1e-9).of(-179.0)
        assertThat(GeoMath.normaliseLongitude(-181.0)).isWithin(1e-9).of(179.0)
        assertThat(GeoMath.normaliseLongitude(540.0)).isWithin(1e-9).of(-180.0)
    }

    // ---- Helpers ---------------------------------------------------------------------

    /**
     * Exact area of the region bounded by two parallels and two meridians on a sphere:
     * `R² · Δλ · (sin φ₂ − sin φ₁)`. Derived independently of the polygon algorithm under
     * test, which is the whole point of using it as the reference.
     */
    private fun analyticSphericalRectangleArea(
        southLatitude: Double,
        northLatitude: Double,
        westLongitude: Double,
        eastLongitude: Double,
    ): Double = GeoMath.EARTH_RADIUS_METRES * GeoMath.EARTH_RADIUS_METRES *
        Math.toRadians(eastLongitude - westLongitude) *
        (sin(Math.toRadians(northLatitude)) - sin(Math.toRadians(southLatitude)))

    private fun sphericalLawOfCosines(
        from: LatLng,
        to: LatLng,
    ): Double {
        val fromLat = Math.toRadians(from.latitude)
        val toLat = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val cosine = sin(fromLat) * sin(toLat) + cos(fromLat) * cos(toLat) * cos(deltaLon)
        return acos(cosine.coerceIn(-1.0, 1.0)) * GeoMath.EARTH_RADIUS_METRES
    }

    private fun coloradoQuadrilateral(): List<LatLng> = listOf(
        LatLng(COLORADO_SOUTH, COLORADO_WEST),
        LatLng(COLORADO_SOUTH, COLORADO_EAST),
        LatLng(COLORADO_NORTH, COLORADO_EAST),
        LatLng(COLORADO_NORTH, COLORADO_WEST),
    )

    private fun relativeError(
        actual: Double,
        expected: Double,
    ): Double = abs(actual - expected) / abs(expected)

    private companion object {
        val PARIS = LatLng(48.8566, 2.3522)
        val BERLIN = LatLng(52.5200, 13.4050)
        val ADDIS_ABABA = LatLng(9.0300, 38.7400)
        val NAIROBI = LatLng(-1.2833, 36.8167)

        /** BUILD_PLAN phase 2: area must match a reference value within 0.1%. */
        const val REFERENCE_TOLERANCE = 0.001

        /** Published city-pair distances are quoted to the nearest kilometre at best. */
        const val PUBLISHED_TOLERANCE = 0.01

        // Colorado's legal boundary: 37°N to 41°N, 102°03'W to 109°03'W.
        const val COLORADO_SOUTH = 37.0
        const val COLORADO_NORTH = 41.0
        const val COLORADO_WEST = -109.05
        const val COLORADO_EAST = -102.05
    }
}
