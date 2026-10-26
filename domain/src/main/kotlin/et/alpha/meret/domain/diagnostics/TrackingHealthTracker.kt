package et.alpha.meret.domain.diagnostics

import et.alpha.meret.core.common.telemetry.NoOpTelemetry
import et.alpha.meret.core.common.telemetry.Telemetry
import et.alpha.meret.core.common.tracking.TrackingGap
import et.alpha.meret.core.common.tracking.TrackingHealthMonitor

/**
 * Turns the fix timeline of a recording into a [TrackingHealth] snapshot: a
 * [TrackingHealthMonitor] for the window and the gaps, plus the last fix's accuracy and
 * satellite count, plus a bounded gap log.
 *
 * Every method returns the new snapshot, so the caller can publish it as it likes. All times
 * are `elapsedRealtimeNanos`. Thread-safe: the fix collector, the gap ticker and the
 * controller's commands call it from different coroutines.
 *
 * Each gap is also sent to [telemetry] once, when it closes, as a `tracking_gap` event with
 * its duration only: no position, no time of day. The default binding discards it.
 */
public class TrackingHealthTracker(
    private val telemetry: Telemetry = NoOpTelemetry,
) {
    private val lock = Any()
    private var monitor: TrackingHealthMonitor? = null
    private var health = TrackingHealth()

    public val current: TrackingHealth
        get() = synchronized(lock) { health }

    /**
     * A recording starts, or is picked up after process death, sampling every
     * [samplingIntervalMillis]. Clears everything from an earlier recording. Recording time
     * starts at the first [resume].
     */
    public fun begin(samplingIntervalMillis: Long): TrackingHealth = synchronized(lock) {
        monitor = TrackingHealthMonitor(samplingIntervalMillis)
        health = TrackingHealth(samplingIntervalMillis = samplingIntervalMillis)
        health
    }

    /** The engine starts delivering fixes at [atElapsedNanos]. */
    public fun resume(atElapsedNanos: Long): TrackingHealth = synchronized(lock) {
        val active = monitor ?: return health
        active.resume(atElapsedNanos)
        publish(active, atElapsedNanos)
    }

    /** The engine stops at [atElapsedNanos]. An open gap ends here. */
    public fun pause(atElapsedNanos: Long): TrackingHealth = synchronized(lock) {
        val active = monitor ?: return health
        active.pause(atElapsedNanos)
        closeOpenGap(atElapsedNanos)
        publish(active, atElapsedNanos)
    }

    /** The recording has ended. The last snapshot stays readable, marked not recording. */
    public fun end(atElapsedNanos: Long): TrackingHealth = synchronized(lock) {
        pause(atElapsedNanos)
        monitor = null
        health = health.copy(isRecording = false)
        health
    }

    /** A fix arrived. Ignored while paused or before [begin]. */
    public fun onFix(
        atElapsedNanos: Long,
        accuracyMetres: Double,
        satellitesUsed: Int?,
    ): TrackingHealth = synchronized(lock) {
        val active = monitor
        if (active == null || !active.isRecording) return health
        val closed = active.onFix(atElapsedNanos)
        closeOpenGap(atElapsedNanos)
        if (closed != null) {
            log(closed.toRecord())
            report(closed.durationMillis)
        }
        health =
            health.copy(
                fixCount = health.fixCount + 1,
                lastFixElapsedNanos = atElapsedNanos,
                lastAccuracyMetres = accuracyMetres,
                lastSatellitesUsed = satellitesUsed,
            )
        publish(active, atElapsedNanos)
    }

    /** Polled on a timer: logs a gap that is still open, and refreshes the window. */
    public fun tick(nowElapsedNanos: Long): TrackingHealth = synchronized(lock) {
        val active = monitor ?: return health
        active.check(nowElapsedNanos)?.let { log(it.toRecord()) }
        refreshOpenGap(nowElapsedNanos)
        publish(active, nowElapsedNanos)
    }

    private fun publish(
        active: TrackingHealthMonitor,
        nowElapsedNanos: Long,
    ): TrackingHealth {
        val window = active.windowHealth(nowElapsedNanos)
        health =
            health.copy(
                isRecording = active.isRecording,
                windowExpected = window.expected,
                windowActual = window.actual,
            )
        return health
    }

    private fun log(gap: GapRecord) {
        health = health.copy(gaps = (health.gaps + gap).takeLast(MAX_GAPS))
    }

    /** An open gap's duration grows on every tick until a fix or a pause closes it. */
    private fun refreshOpenGap(nowElapsedNanos: Long) {
        replaceOpenGap { it.copy(durationMillis = millisBetween(it, nowElapsedNanos)) }
    }

    private fun closeOpenGap(atElapsedNanos: Long) {
        replaceOpenGap {
            it.copy(durationMillis = millisBetween(it, atElapsedNanos), isOpen = false)
        }?.let { report(it.durationMillis) }
    }

    /** Returns the replacement, or null when no gap is open. */
    private inline fun replaceOpenGap(transform: (GapRecord) -> GapRecord): GapRecord? {
        val last = health.gaps.lastOrNull()
        if (last == null || !last.isOpen) return null
        val replaced = transform(last)
        health = health.copy(gaps = health.gaps.dropLast(1) + replaced)
        return replaced
    }

    /** A gap has closed. Its duration is the only thing sent. */
    private fun report(durationMillis: Long) {
        telemetry.recordEvent(GAP_EVENT, mapOf(DURATION_ATTRIBUTE to durationMillis.toString()))
    }

    private fun millisBetween(
        gap: GapRecord,
        endElapsedNanos: Long,
    ): Long = (endElapsedNanos - gap.startElapsedNanos) / NANOS_PER_MILLI

    private fun TrackingGap.toRecord() = GapRecord(
        startElapsedNanos = startElapsedNanos,
        durationMillis = durationMillis,
        isOpen = endElapsedNanos == null,
    )

    public companion object {
        /** Enough for a day of a badly behaved phone; older gaps are dropped first. */
        public const val MAX_GAPS: Int = 20

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val GAP_EVENT = "tracking_gap"
        private const val DURATION_ATTRIBUTE = "duration_ms"
    }
}
