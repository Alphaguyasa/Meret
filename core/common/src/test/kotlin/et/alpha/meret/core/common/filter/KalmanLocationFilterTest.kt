package et.alpha.meret.core.common.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.fixture.TraceFixtures
import et.alpha.meret.core.common.fixture.crossTrackMetres
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test
import kotlin.math.sqrt

class KalmanLocationFilterTest {
    @Test
    fun `filtering a noisy straight walk halves the cross-track error`() {
        val truth = TraceFixtures.straightWalk(pointCount = 600, spacingMetres = 1.4)
        val noisy = TraceFixtures.withGaussianNoise(truth, sigmaMetres = 8.0, seed = 4242L)

        val filtered = runFilter(noisy, accuracyMetres = 8.0)

        val rawError = crossTrackRms(noisy, truth.first(), truth.last())
        val filteredError = crossTrackRms(filtered, truth.first(), truth.last())

        println(
            "Kalman cross-track RMS: raw ${"%.2f".format(rawError)} m -> " +
                "filtered ${"%.2f".format(filteredError)} m",
        )

        assertThat(filteredError).isLessThan(rawError / 2.0)
    }

    @Test
    fun `filtering stops noise from inflating the measured path length`() {
        // The failure this guards against is the one field teams actually notice: a raw
        // track reports several kilometres for a walk that was one, because every jitter
        // between consecutive fixes is counted as travel.
        val truth = TraceFixtures.straightWalk(pointCount = 600, spacingMetres = 1.4)
        val trueLength = GeoMath.pathLength(truth)
        val noisy = TraceFixtures.withGaussianNoise(truth, sigmaMetres = 8.0, seed = 99L)

        val filtered = runFilter(noisy, accuracyMetres = 8.0)

        val rawLength = GeoMath.pathLength(noisy)
        val filteredLength = GeoMath.pathLength(filtered)

        println(
            "Path length: true ${"%.0f".format(trueLength)} m, " +
                "raw ${"%.0f".format(rawLength)} m, filtered ${"%.0f".format(filteredLength)} m",
        )

        assertThat(rawLength).isGreaterThan(trueLength * 2)
        assertThat(filteredLength).isLessThan(rawLength / 2.0)
    }

    @Test
    fun `a stationary device converges instead of drifting`() {
        val jitter = TraceFixtures.stationary(pointCount = 600, sigmaMetres = 10.0, seed = 7L)

        val filtered = runFilter(jitter, accuracyMetres = 10.0)

        // Compare the settled tail, not the whole run: the filter starts at the first
        // (noisy) fix and needs a few seconds to pull in.
        val rawOffset = meanOffsetFromTruth(jitter.takeLast(300))
        val filteredOffset = meanOffsetFromTruth(filtered.takeLast(300))

        println(
            "Stationary mean offset: raw ${"%.2f".format(rawOffset)} m -> " +
                "filtered ${"%.2f".format(filteredOffset)} m",
        )

        assertThat(filteredOffset).isLessThan(rawOffset / 2.0)
        assertThat(filteredOffset).isLessThan(6.0)
    }

    @Test
    fun `the first fix is passed through untouched and flagged as a reset`() {
        val filter = KalmanLocationFilter()
        val fix = LatLng(9.0, 38.0)

        val result = filter.process(fix, accuracyMetres = 5.0, timestampNanos = 1_000_000_000L)

        assertThat(result.position).isEqualTo(fix)
        assertThat(result.wasReset).isTrue()
        assertThat(result.varianceSquareMetres).isEqualTo(25.0)
        assertThat(filter.isInitialised).isTrue()
    }

    @Test
    fun `variance shrinks as fixes accumulate`() {
        val filter = KalmanLocationFilter()
        val point = LatLng(9.0, 38.0)

        val first = filter.process(point, 10.0, seconds(1))
        var last = first
        repeat(30) { index -> last = filter.process(point, 10.0, seconds(2 + index)) }

        assertThat(last.varianceSquareMetres).isLessThan(first.varianceSquareMetres)
        assertThat(filter.varianceSquareMetres).isEqualTo(last.varianceSquareMetres)
    }

    @Test
    fun `reset makes the next fix authoritative again`() {
        val filter = KalmanLocationFilter()
        filter.process(LatLng(9.0, 38.0), 5.0, seconds(1))
        filter.process(LatLng(9.0, 38.0), 5.0, seconds(2))

        filter.reset()
        assertThat(filter.isInitialised).isFalse()
        assertThat(filter.varianceSquareMetres).isNull()

        val jumped = LatLng(9.5, 38.5)
        val result = filter.process(jumped, 5.0, seconds(3))

        assertThat(result.position).isEqualTo(jumped)
        assertThat(result.wasReset).isTrue()
    }

    @Test
    fun `a gap longer than the reset window restarts the filter`() {
        val filter = KalmanLocationFilter(gapResetSeconds = 60.0)
        filter.process(LatLng(9.0, 38.0), 5.0, seconds(0))

        val withinWindow = filter.process(LatLng(9.0, 38.0001), 5.0, seconds(59))
        val afterGap = filter.process(LatLng(9.001, 38.001), 5.0, seconds(59 + 61))

        assertThat(withinWindow.wasReset).isFalse()
        assertThat(afterGap.wasReset).isTrue()
        assertThat(afterGap.position).isEqualTo(LatLng(9.001, 38.001))
    }

    @Test
    fun `an out-of-order timestamp is treated as zero elapsed time`() {
        // Ordering is supposed to come from elapsedRealtimeNanos, which cannot go
        // backwards — but the filter must not produce a shrinking variance if it does.
        val filter = KalmanLocationFilter()
        filter.process(LatLng(9.0, 38.0), 5.0, seconds(10))
        val before = checkNotNull(filter.varianceSquareMetres)

        val result = filter.process(LatLng(9.0, 38.0), 5.0, seconds(5))

        assertThat(result.wasReset).isFalse()
        assertThat(result.varianceSquareMetres).isLessThan(before)
        assertThat(result.varianceSquareMetres).isGreaterThan(0.0)
    }

    @Test
    fun `a zero accuracy fix does not pin the estimate to itself`() {
        // Some devices report accuracy 0. Taken literally that is a perfect measurement,
        // and the gain would go to 1 for every subsequent fix.
        val filter = KalmanLocationFilter()
        filter.process(LatLng(9.0, 38.0), accuracyMetres = 0.0, timestampNanos = seconds(1))

        val result = filter.process(LatLng(9.1, 38.0), accuracyMetres = 0.0, seconds(2))

        assertThat(result.position.latitude).isGreaterThan(9.0)
        assertThat(result.position.latitude).isLessThan(9.1)
    }

    @Test
    fun `a larger process noise tracks movement more closely`() {
        val truth = TraceFixtures.straightWalk(pointCount = 200, spacingMetres = 1.4)

        val sluggish = runFilter(truth, accuracyMetres = 8.0, processNoise = 0.1)
        val responsive = runFilter(truth, accuracyMetres = 8.0, processNoise = 20.0)

        val sluggishLag = GeoMath.haversineDistance(sluggish.last(), truth.last())
        val responsiveLag = GeoMath.haversineDistance(responsive.last(), truth.last())

        assertThat(responsiveLag).isLessThan(sluggishLag)
    }

    private fun runFilter(
        trace: List<LatLng>,
        accuracyMetres: Double,
        processNoise: Double = KalmanLocationFilter.DEFAULT_PROCESS_NOISE,
    ): List<LatLng> {
        val filter = KalmanLocationFilter(processNoiseSquareMetresPerSecond = processNoise)
        return trace.mapIndexed { index, point ->
            filter.process(point, accuracyMetres, seconds(index)).position
        }
    }

    private fun meanOffsetFromTruth(points: List<LatLng>): Double =
        points.sumOf { GeoMath.haversineDistance(it, TraceFixtures.ADDIS_ABABA) } / points.size

    private fun crossTrackRms(
        points: List<LatLng>,
        lineStart: LatLng,
        lineEnd: LatLng,
    ): Double {
        val sumOfSquares =
            points.sumOf { point ->
                val deviation = crossTrackMetres(point, lineStart, lineEnd)
                deviation * deviation
            }
        return sqrt(sumOfSquares / points.size)
    }

    private fun seconds(value: Int): Long = value.toLong() * 1_000_000_000L
}
