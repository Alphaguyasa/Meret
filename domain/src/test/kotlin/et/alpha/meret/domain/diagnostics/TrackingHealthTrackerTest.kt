package et.alpha.meret.domain.diagnostics

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.telemetry.Telemetry
import org.junit.Test

class TrackingHealthTrackerTest {
    private val telemetry = RecordingTelemetry()
    private val tracker = TrackingHealthTracker(telemetry)

    @Test
    fun `before a recording there is nothing to report`() {
        val health = tracker.onFix(at(seconds = 1), accuracyMetres = 5.0, satellitesUsed = 9)

        assertThat(health).isEqualTo(TrackingHealth())
        assertThat(tracker.tick(at(seconds = 600))).isEqualTo(TrackingHealth())
    }

    @Test
    fun `fixes on time give a full window and the last accuracy`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        (1..10).forEach {
            tracker.onFix(at(seconds = it * 5L), accuracyMetres = 4.0 + it, satellitesUsed = it)
        }

        val health = tracker.tick(at(seconds = 50))

        assertThat(health.isRecording).isTrue()
        assertThat(health.windowExpected).isEqualTo(10)
        assertThat(health.windowActual).isEqualTo(10)
        assertThat(health.windowRatio).isEqualTo(1.0)
        assertThat(health.fixCount).isEqualTo(10)
        assertThat(health.lastAccuracyMetres).isEqualTo(14.0)
        assertThat(health.lastSatellitesUsed).isEqualTo(10)
        assertThat(health.lastFixElapsedNanos).isEqualTo(at(seconds = 50))
        assertThat(health.gaps).isEmpty()
    }

    /** The battery-killer signature: the service is alive but fixes stop. */
    @Test
    fun `a gap is logged open by the ticker, then closed by the next fix`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.onFix(at(seconds = 5), accuracyMetres = 5.0, satellitesUsed = 8)

        val open = tracker.tick(at(seconds = 25))
        assertThat(open.gaps).containsExactly(GapRecord(at(seconds = 5), 20_000, isOpen = true))

        val growing = tracker.tick(at(seconds = 65))
        assertThat(growing.gaps).containsExactly(GapRecord(at(seconds = 5), 60_000, isOpen = true))

        val closed = tracker.onFix(at(seconds = 125), accuracyMetres = 5.0, satellitesUsed = 8)
        assertThat(closed.gaps).containsExactly(GapRecord(at(seconds = 5), 120_000, isOpen = false))
    }

    @Test
    fun `a gap the ticker missed is logged when the fix that ends it arrives`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))

        val health = tracker.onFix(at(seconds = 40), accuracyMetres = 5.0, satellitesUsed = null)

        assertThat(health.gaps).containsExactly(GapRecord(at(seconds = 0), 40_000, isOpen = false))
    }

    @Test
    fun `a pause closes an open gap and paused time is not a gap`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.tick(at(seconds = 30))

        val paused = tracker.pause(at(seconds = 45))
        assertThat(paused.isRecording).isFalse()
        assertThat(paused.gaps).containsExactly(GapRecord(at(seconds = 0), 45_000, isOpen = false))

        tracker.resume(at(seconds = 3_600))
        val resumed = tracker.tick(at(seconds = 3_605))
        assertThat(resumed.isRecording).isTrue()
        assertThat(resumed.gaps).hasSize(1)
    }

    @Test
    fun `fixes while paused are ignored`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.pause(at(seconds = 1))

        val health = tracker.onFix(at(seconds = 2), accuracyMetres = 5.0, satellitesUsed = 7)

        assertThat(health.fixCount).isEqualTo(0)
        assertThat(health.lastAccuracyMetres).isNull()
    }

    @Test
    fun `the gap log keeps only the most recent gaps`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        val count = TrackingHealthTracker.MAX_GAPS + 5
        (1..count).forEach {
            tracker.onFix(at(seconds = it * 60L), accuracyMetres = 5.0, satellitesUsed = null)
        }

        val gaps = tracker.current.gaps

        assertThat(gaps).hasSize(TrackingHealthTracker.MAX_GAPS)
        assertThat(gaps.last().startElapsedNanos).isEqualTo(at(seconds = (count - 1) * 60L))
    }

    @Test
    fun `end keeps the last figures and a new recording starts clean`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.onFix(at(seconds = 5), accuracyMetres = 6.0, satellitesUsed = 9)

        val ended = tracker.end(at(seconds = 6))
        assertThat(ended.isRecording).isFalse()
        assertThat(ended.fixCount).isEqualTo(1)
        assertThat(tracker.onFix(at(seconds = 7), 5.0, 9).fixCount).isEqualTo(1)

        val next = tracker.begin(INTERVAL_MS * 2)
        assertThat(next).isEqualTo(TrackingHealth(samplingIntervalMillis = INTERVAL_MS * 2))
    }

    @Test
    fun `a gap is reported once, when it closes, with its duration only`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.onFix(at(seconds = 5), accuracyMetres = 5.0, satellitesUsed = 8)
        tracker.tick(at(seconds = 25))
        tracker.tick(at(seconds = 65))
        assertThat(telemetry.events).isEmpty()

        tracker.onFix(at(seconds = 125), accuracyMetres = 5.0, satellitesUsed = 8)
        tracker.onFix(at(seconds = 130), accuracyMetres = 5.0, satellitesUsed = 8)

        assertThat(telemetry.events)
            .containsExactly("tracking_gap" to mapOf("duration_ms" to "120000"))
    }

    @Test
    fun `a gap the ticker missed is reported when the fix that ends it arrives`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))

        tracker.onFix(at(seconds = 40), accuracyMetres = 5.0, satellitesUsed = null)

        assertThat(telemetry.events)
            .containsExactly("tracking_gap" to mapOf("duration_ms" to "40000"))
    }

    @Test
    fun `a gap ended by a pause is reported, and the paused time is not`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        tracker.tick(at(seconds = 30))
        tracker.pause(at(seconds = 45))

        tracker.resume(at(seconds = 3_600))
        tracker.onFix(at(seconds = 3_605), accuracyMetres = 5.0, satellitesUsed = null)
        tracker.end(at(seconds = 3_606))

        assertThat(telemetry.events)
            .containsExactly("tracking_gap" to mapOf("duration_ms" to "45000"))
    }

    @Test
    fun `fixes on time report nothing`() {
        tracker.begin(INTERVAL_MS)
        tracker.resume(at(seconds = 0))
        (1..10).forEach {
            tracker.onFix(at(seconds = it * 5L), accuracyMetres = 5.0, satellitesUsed = 8)
            tracker.tick(at(seconds = it * 5L))
        }
        tracker.end(at(seconds = 51))

        assertThat(telemetry.events).isEmpty()
    }

    private fun at(seconds: Long): Long = BASE_NANOS + seconds * NANOS_PER_SECOND

    private class RecordingTelemetry : Telemetry {
        val events = mutableListOf<Pair<String, Map<String, String>>>()

        override fun recordNonFatal(
            throwable: Throwable,
            attributes: Map<String, String>,
        ): Unit = error("not expected")

        override fun recordEvent(
            name: String,
            attributes: Map<String, String>,
        ) {
            events += name to attributes
        }

        override fun setAttribute(
            key: String,
            value: String,
        ): Unit = error("not expected")
    }

    private companion object {
        const val INTERVAL_MS = 5_000L
        const val BASE_NANOS = 7_200_000_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
