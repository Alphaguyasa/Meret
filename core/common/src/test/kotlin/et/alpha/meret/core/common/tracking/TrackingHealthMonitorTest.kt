package et.alpha.meret.core.common.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** Synthetic timelines at a 1 s sampling interval, so the gap threshold is 3 s. */
class TrackingHealthMonitorTest {
    private val monitor = TrackingHealthMonitor(samplingIntervalMillis = INTERVAL_MS)

    @Test
    fun `a steady stream of fixes has no gap`() {
        monitor.resume(at(0))

        val gaps = (1..120).mapNotNull { second -> monitor.onFix(at(second)) }

        assertThat(gaps).isEmpty()
        assertThat(monitor.check(at(122))).isNull()
    }

    @Test
    fun `a gap is reported when the fix that closes it arrives`() {
        monitor.resume(at(0))
        (1..10).forEach { monitor.onFix(at(it)) }

        val gap = monitor.onFix(at(20))

        assertThat(gap).isEqualTo(TrackingGap(at(10), at(20), durationMillis = 10_000))
        assertThat(monitor.onFix(at(21))).isNull()
    }

    @Test
    fun `an open gap is reported by check once, and not again when it closes`() {
        monitor.resume(at(0))
        monitor.onFix(at(1))

        assertThat(monitor.check(at(4))).isNull()
        val open = monitor.check(at(5))
        val again = monitor.check(at(30))
        val closing = monitor.onFix(at(31))

        assertThat(open).isEqualTo(TrackingGap(at(1), null, durationMillis = 4_000))
        assertThat(again).isNull()
        assertThat(closing).isNull()
    }

    @Test
    fun `exactly three intervals is not a gap`() {
        monitor.resume(at(0))
        monitor.onFix(at(1))

        assertThat(monitor.onFix(at(4))).isNull()
    }

    @Test
    fun `time spent paused is never a gap`() {
        monitor.resume(at(0))
        monitor.onFix(at(1))
        monitor.pause(at(2))

        assertThat(monitor.check(at(60))).isNull()
        assertThat(monitor.onFix(at(61))).isNull()

        monitor.resume(at(300))
        assertThat(monitor.check(at(302))).isNull()
        assertThat(monitor.onFix(at(302))).isNull()
    }

    @Test
    fun `the gap clock restarts on resume`() {
        monitor.resume(at(0))
        monitor.onFix(at(1))
        monitor.pause(at(2))
        monitor.resume(at(100))

        val gap = monitor.check(at(104))

        assertThat(gap).isEqualTo(TrackingGap(at(100), null, durationMillis = 4_000))
    }

    @Test
    fun `window health counts expected and received fixes`() {
        monitor.resume(at(0))
        // Every other second for ten minutes: half the fixes the interval promises.
        (2..600 step 2).forEach { monitor.onFix(at(it)) }

        val health = monitor.windowHealth(at(600))

        assertThat(health).isEqualTo(WindowHealth(expected = 600, actual = 300))
        assertThat(health.ratio).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `paused time is not expected`() {
        monitor.resume(at(0))
        (1..200).forEach { monitor.onFix(at(it)) }
        monitor.pause(at(200))
        monitor.resume(at(500))
        (501..600).forEach { monitor.onFix(at(it)) }

        val health = monitor.windowHealth(at(600))

        assertThat(health).isEqualTo(WindowHealth(expected = 300, actual = 300))
    }

    @Test
    fun `only the trailing window counts`() {
        monitor.resume(at(0))
        (1..1_200).forEach { monitor.onFix(at(it)) }

        val health = monitor.windowHealth(at(1_200))

        // Fixes at 600..1200 inclusive sit inside a window that starts at 600.
        assertThat(health).isEqualTo(WindowHealth(expected = 600, actual = 601))
    }

    @Test
    fun `no recording yet has no ratio`() {
        assertThat(monitor.windowHealth(at(10))).isEqualTo(WindowHealth(expected = 0, actual = 0))
        assertThat(monitor.windowHealth(at(10)).ratio).isNull()
        assertThat(monitor.onFix(at(11))).isNull()
        assertThat(monitor.isRecording).isFalse()
    }

    @Test
    fun `invalid settings are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackingHealthMonitor(samplingIntervalMillis = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrackingHealthMonitor(samplingIntervalMillis = 1_000, windowMillis = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrackingHealthMonitor(samplingIntervalMillis = 1_000, gapFactor = 0.5)
        }
    }

    private fun at(second: Int): Long = BASE_NANOS + second * NANOS_PER_SECOND

    private companion object {
        const val INTERVAL_MS = 1_000L
        const val BASE_NANOS = 7_200_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
