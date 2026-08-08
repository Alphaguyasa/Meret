package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng

/** What [AutoPauseDetector] decided after one fix. */
public enum class MotionState {
    MOVING,
    PAUSED,
}

/**
 * Stage 4 of the filter chain: decides when the enumerator has stopped.
 *
 * Auto-pause exists so `duration_moving_ms` means something. A track that ran for six
 * hours of which four were spent filling in forms is not a six-hour walk, and reporting it
 * as one makes every derived rate — plots per hour, km per day — wrong.
 *
 * ## Pausing
 *
 * Both conditions from `LOCATION_ENGINE.md` must hold together: net displacement across
 * the window below [TrackingConfig.autoPauseDisplacementMetres], *and* speed below
 * [TrackingConfig.autoPauseSpeedMetresPerSecond]. Displacement alone would pause someone
 * who walked a tight loop back to where they started; speed alone would pause someone
 * standing still on a moving bus, whose speed the platform reports as their own.
 *
 * The window must be **full** before a pause can trigger. Without that, a track pauses on
 * its second fix — two fixes three seconds apart are always within 15 m of each other.
 *
 * ## Resuming
 *
 * Displacement from the position where the pause began, and the threshold is deliberately
 * larger than the pausing one ([TrackingConfig.autoResumeDisplacementMetres], validated to
 * exceed it). Equal thresholds would flip state on GPS jitter alone, and a notification
 * that alternates between "Paused" and "Recording" every few seconds is worse than no
 * notification.
 *
 * ## Speed
 *
 * Uses the platform's reported speed when there is one. When there is not — some
 * `GPS_PROVIDER` fixes carry no speed at all — it falls back to the window's mean pace,
 * path length over elapsed time. That fallback is an over-estimate in the presence of
 * jitter, which errs toward *not* pausing, which is the safe direction: over-counting
 * moving time is a smaller lie than marking real work as idle.
 *
 * Not thread-safe. One instance per track.
 */
public class AutoPauseDetector(
    private val config: TrackingConfig,
) {
    private class Sample(
        val elapsedRealtimeNanos: Long,
        val position: LatLng,
        val speedMetresPerSecond: Double?,
    )

    private val window = ArrayDeque<Sample>()
    private var pauseAnchor: LatLng? = null

    public var state: MotionState = MotionState.MOVING
        private set

    /** Where the current pause began, or `null` while moving. Exposed for diagnostics. */
    public val pauseAnchorPosition: LatLng?
        get() = pauseAnchor

    /** Clears the window and returns to [MotionState.MOVING]. */
    public fun reset() {
        window.clear()
        pauseAnchor = null
        state = MotionState.MOVING
    }

    /**
     * Offers one fix and returns the state in force afterwards.
     *
     * Positions should be the filtered ones — raw jitter would both delay pauses and
     * trigger spurious resumes.
     *
     * With [TrackingConfig.autoPauseEnabled] false this always reports
     * [MotionState.MOVING] and keeps no window, which is what "a stationary observation
     * point is legitimate survey activity" means in practice.
     */
    public fun update(
        filteredPosition: LatLng,
        elapsedRealtimeNanos: Long,
        speedMetresPerSecond: Double?,
    ): MotionState {
        if (!config.autoPauseEnabled) return MotionState.MOVING

        window.addLast(Sample(elapsedRealtimeNanos, filteredPosition, speedMetresPerSecond))
        trimWindow(elapsedRealtimeNanos)

        state =
            when (state) {
                MotionState.MOVING -> if (shouldPause()) beginPause(filteredPosition) else state
                MotionState.PAUSED -> if (shouldResume(filteredPosition)) endPause() else state
            }
        return state
    }

    /**
     * Drops samples older than the window, always keeping the one sample that straddles
     * the boundary. That straddling sample is what makes the window span *at least* the
     * configured duration rather than up to one sampling interval less than it.
     */
    private fun trimWindow(nowNanos: Long) {
        val cutoff = nowNanos - config.autoPauseWindowMillis * NANOS_PER_MILLI
        while (window.size > 2 && window[1].elapsedRealtimeNanos <= cutoff) {
            window.removeFirst()
        }
    }

    private fun shouldPause(): Boolean {
        val oldest = window.firstOrNull() ?: return false
        val newest = window.last()

        val spanMillis =
            (newest.elapsedRealtimeNanos - oldest.elapsedRealtimeNanos) / NANOS_PER_MILLI
        val displacementMetres = GeoMath.haversineDistance(oldest.position, newest.position)

        // One condition per line, and `&&` short-circuits in cost order: a span check is
        // arithmetic, the displacement is already computed, and only the speed check may walk
        // the whole window.
        return spanMillis >= config.autoPauseWindowMillis &&
            displacementMetres < config.autoPauseDisplacementMetres &&
            windowSpeed(spanMillis) < config.autoPauseSpeedMetresPerSecond
    }

    /**
     * The fastest defensible reading of the window: the highest reported speed in it, or
     * the mean pace when nothing reported one. Taking the maximum rather than the mean of
     * reported speeds means one clear moment of movement inside the window prevents a
     * pause, which is again the safe direction.
     */
    private fun windowSpeed(spanMillis: Long): Double {
        val reported = window.mapNotNull { it.speedMetresPerSecond }

        return when {
            reported.isNotEmpty() -> {
                reported.max()
            }

            spanMillis <= 0L -> {
                0.0
            }

            else -> {
                GeoMath.pathLength(window.map { it.position }) /
                    (spanMillis.toDouble() / MILLIS_PER_SECOND)
            }
        }
    }

    private fun shouldResume(current: LatLng): Boolean {
        val anchor = pauseAnchor ?: return true
        return GeoMath.haversineDistance(anchor, current) >= config.autoResumeDisplacementMetres
    }

    private fun beginPause(at: LatLng): MotionState {
        pauseAnchor = at
        return MotionState.PAUSED
    }

    private fun endPause(): MotionState {
        pauseAnchor = null
        // The window described a stationary device right up to the fix that broke the
        // pause. Keeping it would let the next fix re-pause immediately on samples that
        // predate the movement.
        window.clear()
        return MotionState.MOVING
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
