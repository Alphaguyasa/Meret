package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.filter.KalmanLocationFilter
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng

/** One fix's worth of Kalman output, plus where it sits in the track's segment structure. */
public data class SmoothedFix(
    public val position: LatLng,
    public val varianceSquareMetres: Double,
    /** Zero-based. Increments each time a gap or an explicit boundary breaks the line. */
    public val segmentIndex: Int,
    /** True on the first fix of a new segment — but never on the first fix of the track. */
    public val startedNewSegment: Boolean,
)

/**
 * Stage 2 of the filter chain: smoothing, and the authority on where segments begin.
 *
 * Thin by design. The filter itself is `:core:common`'s [KalmanLocationFilter], which is
 * pure maths with its own tests; this stage supplies it with [TrackingConfig] and turns its
 * `wasReset` signal into a segment index the rest of the app can store and draw with.
 *
 * ## Why segments matter
 *
 * A segment is a run of fixes the app is willing to draw a line between. After a
 * five-minute gap — a tunnel, a dead battery, an OEM killing the service — the device may
 * be a kilometre away, and joining the two ends produces a straight line through terrain
 * nobody walked. That line then gets measured, exported, and shown to a client. So a gap
 * longer than [TrackingConfig.segmentGapSeconds] breaks the line instead, and the map draws
 * one polyline per segment.
 *
 * The same reset also protects the smoothing itself: blending a fix against a
 * five-minute-old estimate would drag the first few fixes of the new segment back toward
 * where the device used to be.
 *
 * Not thread-safe. One instance per track.
 */
public class KalmanStage(
    config: TrackingConfig,
) {
    private val filter =
        KalmanLocationFilter(
            processNoiseSquareMetresPerSecond = config.kalmanProcessNoiseSquareMetresPerSecond,
            gapResetSeconds = config.segmentGapSeconds,
        )

    private var hasSeenAnyFix = false

    public var segmentIndex: Int = 0
        private set

    /**
     * Forces the next fix to start a new segment.
     *
     * Called for an explicit boundary the clock cannot see: a user pause and resume, where
     * two fixes ten seconds apart should still not be joined because the enumerator got
     * into a vehicle in between.
     */
    public fun startNewSegment() {
        filter.reset()
    }

    /** Returns to a fresh-track state: segment 0, no estimate. */
    public fun reset() {
        filter.reset()
        hasSeenAnyFix = false
        segmentIndex = 0
    }

    /**
     * Picks up an existing track at [segmentIndex], with no estimate.
     *
     * The next fix will therefore report `startedNewSegment` and land in
     * `segmentIndex + 1`. That is correct rather than unfortunate: the estimate was lost
     * because the process died, and the app has no idea where the device went in the
     * meantime, so the line must break there.
     */
    public fun resumeAtSegment(segmentIndex: Int) {
        require(segmentIndex >= 0) { "segmentIndex must not be negative, was $segmentIndex" }
        filter.reset()
        hasSeenAnyFix = true
        this.segmentIndex = segmentIndex
    }

    /** Smooths one **accepted** fix. Rejected fixes must never reach this stage. */
    public fun process(fix: RawFix): SmoothedFix {
        val wasFirstEver = !hasSeenAnyFix
        hasSeenAnyFix = true

        val filtered =
            filter.process(
                position = fix.position,
                accuracyMetres = fix.accuracyMetres,
                timestampNanos = fix.elapsedRealtimeNanos,
            )

        val startedNewSegment = filtered.wasReset && !wasFirstEver
        if (startedNewSegment) segmentIndex++

        return SmoothedFix(
            position = filtered.position,
            varianceSquareMetres = filtered.varianceSquareMetres,
            segmentIndex = segmentIndex,
            startedNewSegment = startedNewSegment,
        )
    }
}
