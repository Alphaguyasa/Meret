package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class KalmanStageTest {
    private val config = TrackingConfig()

    @Test
    fun `the first fix of a track does not count as a new segment`() {
        val stage = KalmanStage(config)

        val smoothed = stage.process(fix(nanos = seconds(1)))

        assertThat(smoothed.startedNewSegment).isFalse()
        assertThat(smoothed.segmentIndex).isEqualTo(0)
    }

    @Test
    fun `the first fix is taken at face value`() {
        val stage = KalmanStage(config)

        val smoothed = stage.process(fix(nanos = seconds(1)))

        assertThat(smoothed.position).isEqualTo(START)
    }

    @Test
    fun `consecutive fixes stay in the same segment`() {
        val stage = KalmanStage(config)

        val results = (1..10).map { stage.process(fix(nanos = seconds(it.toLong()))) }

        assertThat(results.map { it.segmentIndex }.distinct()).containsExactly(0)
        assertThat(results.drop(1).none { it.startedNewSegment }).isTrue()
    }

    /**
     * The whole reason segments exist. After a gap the device may be a kilometre away, and
     * joining the two ends draws a confident straight line through terrain nobody walked — which
     * then gets measured and exported.
     */
    @Test
    fun `a gap longer than the configured limit opens a new segment`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))

        val afterGap = stage.process(fix(nanos = seconds(1) + seconds(61)))

        assertThat(afterGap.startedNewSegment).isTrue()
        assertThat(afterGap.segmentIndex).isEqualTo(1)
    }

    @Test
    fun `a gap inside the limit does not open a new segment`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))

        val afterGap = stage.process(fix(nanos = seconds(1) + seconds(59)))

        assertThat(afterGap.startedNewSegment).isFalse()
        assertThat(afterGap.segmentIndex).isEqualTo(0)
    }

    @Test
    fun `the fix opening a new segment is taken at face value again`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))

        val displaced = north(420.0)
        val afterGap = stage.process(fix(position = displaced, nanos = seconds(1) + seconds(300)))

        // Not blended against a five-minute-old estimate, which would drag the first fixes of
        // the new segment back toward where the device used to be.
        assertThat(afterGap.position).isEqualTo(displaced)
    }

    @Test
    fun `an explicit boundary opens a new segment even without a time gap`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))

        stage.startNewSegment()
        val resumed = stage.process(fix(nanos = seconds(2)))

        // A user pause and resume: two fixes a second apart that must still not be joined,
        // because the enumerator may have got into a vehicle in between.
        assertThat(resumed.startedNewSegment).isTrue()
        assertThat(resumed.segmentIndex).isEqualTo(1)
    }

    /**
     * The defining property of the blend: the output lands strictly between the previous
     * estimate and the new measurement, never on either. Asserting the interval rather than a
     * specific figure keeps this a test of behaviour — the exact gain is a function of the
     * process noise and the reported accuracy, and re-tuning those is allowed to move it.
     */
    @Test
    fun `smoothing blends a noisy fix between the estimate and the measurement`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))

        val noisy = north(10.0)
        val smoothed = stage.process(fix(position = noisy, nanos = seconds(2)))

        val fromEstimate = GeoMath.haversineDistance(START, smoothed.position)
        val fromMeasurement = GeoMath.haversineDistance(noisy, smoothed.position)

        assertThat(fromEstimate).isGreaterThan(0.0)
        assertThat(fromMeasurement).isGreaterThan(0.0)
        assertThat(fromEstimate + fromMeasurement).isWithin(0.01).of(10.0)
    }

    /**
     * Smoothing has to get *stronger* as the reported accuracy gets worse — a fix admitting to
     * 25 m of uncertainty should move the estimate less than one claiming 2 m.
     */
    @Test
    fun `a less accurate fix moves the estimate less`() {
        fun displacementFor(accuracy: Double): Double {
            val stage = KalmanStage(config)
            stage.process(fix(nanos = seconds(1)))
            val smoothed =
                stage.process(
                    RawFix(
                        position = north(10.0),
                        accuracyMetres = accuracy,
                        elapsedRealtimeNanos = seconds(2),
                        recordedAtMillis = 1_773_000_000_000L,
                        provider = LocationProvider.GPS,
                    ),
                )
            return GeoMath.haversineDistance(START, smoothed.position)
        }

        assertThat(displacementFor(25.0)).isLessThan(displacementFor(2.0))
    }

    @Test
    fun `reset returns to segment zero with no estimate`() {
        val stage = KalmanStage(config)
        stage.process(fix(nanos = seconds(1)))
        stage.startNewSegment()
        stage.process(fix(nanos = seconds(2)))

        stage.reset()
        val fresh = stage.process(fix(nanos = seconds(3)))

        assertThat(stage.segmentIndex).isEqualTo(0)
        assertThat(fresh.startedNewSegment).isFalse()
        assertThat(fresh.position).isEqualTo(START)
    }

    /**
     * A restarted service picks up an existing track. The next fix must open a *new* segment,
     * because the in-memory estimate died with the process and the app has no idea where the
     * device went in the meantime.
     */
    @Test
    fun `resuming at a segment opens the next one on the following fix`() {
        val stage = KalmanStage(config)

        stage.resumeAtSegment(4)
        val resumed = stage.process(fix(nanos = seconds(1)))

        assertThat(resumed.startedNewSegment).isTrue()
        assertThat(resumed.segmentIndex).isEqualTo(5)
    }

    @Test
    fun `resuming rejects a negative segment index`() {
        val stage = KalmanStage(config)

        val failure = runCatching { stage.resumeAtSegment(-1) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a shorter configured gap breaks segments sooner`() {
        val stage = KalmanStage(TrackingConfig(segmentGapSeconds = 10.0))
        stage.process(fix(nanos = seconds(1)))

        val afterGap = stage.process(fix(nanos = seconds(1) + seconds(11)))

        assertThat(afterGap.startedNewSegment).isTrue()
    }

    private fun fix(
        position: LatLng = START,
        nanos: Long = seconds(1),
    ) = RawFix(
        position = position,
        accuracyMetres = 4.0,
        elapsedRealtimeNanos = nanos,
        recordedAtMillis = 1_773_000_000_000L,
        provider = LocationProvider.GPS,
    )

    private fun north(metres: Double): LatLng = GeoMath.destinationPoint(START, metres, 0.0)

    private fun seconds(value: Long) = value * 1_000_000_000L

    private companion object {
        val START = LatLng(8.9806, 38.7578)
    }
}
