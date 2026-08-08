package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class AutoPauseDetectorTest {
    private val config = TrackingConfig()

    /**
     * The window must fill before a pause can fire. Without this the track pauses on its second
     * fix — any two fixes three seconds apart are within 15 m of each other.
     */
    @Test
    fun `does not pause before the window is full`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        // 59 seconds of standing still: one second short.
        for (second in 0..59) {
            state = detector.update(START, seconds(second), speedMetresPerSecond = 0.0)
        }

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    @Test
    fun `pauses once a full window of stillness has passed`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        for (second in 0..60) {
            state = detector.update(START, seconds(second), speedMetresPerSecond = 0.0)
        }

        assertThat(state).isEqualTo(MotionState.PAUSED)
        assertThat(detector.pauseAnchorPosition).isEqualTo(START)
    }

    /**
     * Displacement alone is not enough. Someone standing on a moving bus has the bus's speed
     * reported as their own, and pausing them would under-count the transit leg.
     */
    @Test
    fun `does not pause while a fix still reports movement`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        for (second in 0..90) {
            state = detector.update(START, seconds(second), speedMetresPerSecond = 1.4)
        }

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    /**
     * One clear moment of movement inside the window prevents a pause, because [windowSpeed]
     * takes the maximum rather than the mean. Erring toward not pausing is the safe direction:
     * over-counting moving time is a smaller lie than marking real work as idle.
     */
    @Test
    fun `a single moving fix inside the window prevents a pause`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        for (second in 0..60) {
            val speed = if (second == 30) 1.4 else 0.0
            state = detector.update(START, seconds(second), speedMetresPerSecond = speed)
        }

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    @Test
    fun `resumes after moving beyond the resume threshold`() {
        val detector = pausedDetector()

        val state = detector.update(north(25.0), seconds(61), speedMetresPerSecond = 1.4)

        assertThat(state).isEqualTo(MotionState.MOVING)
        assertThat(detector.pauseAnchorPosition).isNull()
    }

    /**
     * Hysteresis. The resume threshold is above the pause threshold on purpose: equal values
     * would flip state on GPS jitter, and a notification alternating between "Paused" and
     * "Recording" every few seconds is worse than none.
     */
    @Test
    fun `stays paused between the pause and resume thresholds`() {
        val detector = pausedDetector()

        val state = detector.update(north(17.0), seconds(61), speedMetresPerSecond = 0.4)

        assertThat(state).isEqualTo(MotionState.PAUSED)
    }

    @Test
    fun `does not immediately re-pause after resuming`() {
        val detector = pausedDetector()
        detector.update(north(25.0), seconds(61), speedMetresPerSecond = 1.4)

        // The window that described a stationary device is cleared on resume, so it takes a
        // fresh full window of stillness to pause again rather than one more fix.
        val state = detector.update(north(25.0), seconds(62), speedMetresPerSecond = 0.0)

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    /**
     * A stationary observation point is legitimate survey activity, so switching auto-pause off
     * has to mean off — not "pause anyway but do not tell anyone".
     */
    @Test
    fun `never pauses when disabled`() {
        val detector = AutoPauseDetector(TrackingConfig(autoPauseEnabled = false))

        var state = MotionState.MOVING
        for (second in 0..600) {
            state = detector.update(START, seconds(second), speedMetresPerSecond = 0.0)
        }

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    /**
     * Some `GPS_PROVIDER` fixes carry no speed at all, so the detector derives one from the
     * window's path length. A device that never moved has a near-zero derived pace and must
     * still pause.
     */
    @Test
    fun `pauses on derived speed when no fix reports one`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        for (second in 0..60) {
            state = detector.update(START, seconds(second), speedMetresPerSecond = null)
        }

        assertThat(state).isEqualTo(MotionState.PAUSED)
    }

    @Test
    fun `does not pause on derived speed when the window shows real walking`() {
        val detector = AutoPauseDetector(config)

        var state = MotionState.MOVING
        for (second in 0..90) {
            // 1.4 m/s along a line: displacement across the window is well over 15 m anyway,
            // but this also proves the derived pace is above the threshold.
            state = detector.update(north(second * 1.4), seconds(second), null)
        }

        assertThat(state).isEqualTo(MotionState.MOVING)
    }

    @Test
    fun `reset returns to moving and clears the anchor`() {
        val detector = pausedDetector()

        detector.reset()

        assertThat(detector.state).isEqualTo(MotionState.MOVING)
        assertThat(detector.pauseAnchorPosition).isNull()
    }

    /** A detector that has just paused, at [START]. */
    private fun pausedDetector(): AutoPauseDetector {
        val detector = AutoPauseDetector(config)
        for (second in 0..60) {
            detector.update(START, seconds(second), speedMetresPerSecond = 0.0)
        }
        check(detector.state == MotionState.PAUSED) { "fixture failed to reach a pause" }
        return detector
    }

    private fun north(metres: Double): LatLng = GeoMath.destinationPoint(START, metres, 0.0)

    private fun seconds(value: Int) = value * 1_000_000_000L

    private companion object {
        val START = LatLng(8.9806, 38.7578)
    }
}
