package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class DistanceAccumulatorTest {
    private val config = TrackingConfig()

    @Test
    fun `the first fix books no distance and only sets the anchor`() {
        val accumulator = DistanceAccumulator(config)

        val step = accumulator.accumulate(START, accuracyMetres = 4.0)

        assertThat(step).isEqualTo(0.0)
        assertThat(accumulator.totalMetres).isEqualTo(0.0)
        assertThat(accumulator.anchorPosition).isEqualTo(START)
    }

    @Test
    fun `a step clearing the gate is booked in full`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)

        val step = accumulator.accumulate(north(10.0), 4.0)

        assertThat(step).isWithin(TOLERANCE).of(10.0)
        assertThat(accumulator.totalMetres).isWithin(TOLERANCE).of(10.0)
    }

    @Test
    fun `a step below the minimum books nothing`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)

        val step = accumulator.accumulate(north(3.0), 4.0)

        assertThat(step).isEqualTo(0.0)
        assertThat(accumulator.totalMetres).isEqualTo(0.0)
    }

    /**
     * With 30 m accuracy the gate is `30 * 0.5 = 15 m`, not the 4 m floor. A 10 m step from a
     * fix that admits to being 30 m uncertain has not demonstrably moved at all.
     */
    @Test
    fun `a poor accuracy raises the gate above the floor`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 30.0)

        assertThat(accumulator.accumulate(north(10.0), 30.0)).isEqualTo(0.0)
        assertThat(accumulator.accumulate(north(20.0), 30.0)).isWithin(TOLERANCE).of(20.0)
    }

    /**
     * The anchor stays put on a rejected step, so a slow walk is not silently discarded — three
     * 1.5 m steps against a fixed anchor become one 4.5 m step that clears the gate. This is
     * what keeps the 2% distance bar reachable on a 1 Hz trace where every individual step is
     * below the 4 m floor.
     */
    @Test
    fun `sub-threshold steps accumulate against a stationary anchor until they clear`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)

        assertThat(accumulator.accumulate(north(1.5), 4.0)).isEqualTo(0.0)
        assertThat(accumulator.accumulate(north(3.0), 4.0)).isEqualTo(0.0)
        val booked = accumulator.accumulate(north(4.5), 4.0)

        assertThat(booked).isWithin(TOLERANCE).of(4.5)
        assertThat(accumulator.totalMetres).isWithin(TOLERANCE).of(4.5)
    }

    /**
     * A stationary device must not creep. Jitter is zero-mean in position but strictly positive
     * in distance, so without the gate this walks several kilometres a day.
     */
    @Test
    fun `oscillating around a point books nothing`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)

        repeat(100) { index ->
            accumulator.accumulate(north(if (index % 2 == 0) 1.8 else -1.8), 4.0)
        }

        assertThat(accumulator.totalMetres).isEqualTo(0.0)
    }

    @Test
    fun `starting a new segment keeps the total but drops the anchor`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)
        accumulator.accumulate(north(10.0), 4.0)

        accumulator.startNewSegment()
        // 420 m away, as after a five minute gap. Nobody observed that travel.
        val step = accumulator.accumulate(north(420.0), 4.0)

        assertThat(step).isEqualTo(0.0)
        assertThat(accumulator.totalMetres).isWithin(TOLERANCE).of(10.0)
    }

    @Test
    fun `reset clears both the total and the anchor`() {
        val accumulator = DistanceAccumulator(config)
        accumulator.accumulate(START, 4.0)
        accumulator.accumulate(north(10.0), 4.0)

        accumulator.reset()

        assertThat(accumulator.totalMetres).isEqualTo(0.0)
        assertThat(accumulator.anchorPosition).isNull()
    }

    @Test
    fun `resuming restores the total without booking the first step`() {
        val accumulator = DistanceAccumulator(config)

        accumulator.resumeAt(1_234.5)
        val step = accumulator.accumulate(north(500.0), 4.0)

        assertThat(step).isEqualTo(0.0)
        assertThat(accumulator.totalMetres).isEqualTo(1_234.5)
    }

    @Test
    fun `resuming rejects a negative total`() {
        val accumulator = DistanceAccumulator(config)

        val failure = runCatching { accumulator.resumeAt(-1.0) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a zero minimum step books every movement`() {
        val accumulator =
            DistanceAccumulator(TrackingConfig(minStepMetres = 0.0, stepAccuracyFactor = 0.0))
        accumulator.accumulate(START, 4.0)

        assertThat(accumulator.accumulate(north(0.5), 4.0)).isWithin(TOLERANCE).of(0.5)
    }

    private fun north(metres: Double): LatLng =
        GeoMath.destinationPoint(START, kotlin.math.abs(metres), if (metres >= 0) 0.0 else 180.0)

    private companion object {
        val START = LatLng(8.9806, 38.7578)

        /** Sub-millimetre. The geometry is exact; this only absorbs double rounding. */
        const val TOLERANCE = 0.001
    }
}
