package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.TrackingConfig

/**
 * One fix, fully processed: the row to persist plus the running totals it moved.
 *
 * [point] is always present, for a rejected fix as much as an accepted one — invariant 2
 * in `CLAUDE.md` is that filtering never deletes a raw fix. A rejected point carries
 * `isOutlier = true` and a null [TrackPoint.filteredPosition], because it was never fed to
 * the filter.
 *
 * [totalDistanceMetres] and [movingDurationMillis] are absolute running totals, not deltas,
 * so a caller writing a batch can take the last element and store it on the track without
 * summing anything.
 */
public data class ProcessedFix(
    public val point: TrackPoint,
    public val rejectionReason: RejectionReason?,
    /** Distance this fix added. Zero for a rejected fix or one that failed the step gate. */
    public val stepMetres: Double,
    public val totalDistanceMetres: Double,
    public val movingDurationMillis: Long,
    public val motionState: MotionState,
    public val startedNewSegment: Boolean,
) {
    public val wasAccepted: Boolean
        get() = rejectionReason == null
}

/**
 * The filter chain, assembled.
 *
 * Owns one instance of each stage and runs them in the order `LOCATION_ENGINE.md`
 * specifies: validity gate → Kalman → distance → auto-pause. Simplification is stage 5 in
 * the doc and is deliberately absent here — it runs on track completion, over the whole
 * geometry, and putting it on the fix path would re-simplify the entire track on every
 * fix.
 *
 * ## The rejection short-circuit
 *
 * A rejected fix is recorded and then abandoned. It does not advance the Kalman estimate,
 * the distance anchor, the auto-pause window, or the gate's own reference fix. This is the
 * single most important property of the chain: a 200 m outlier that reached the filter
 * would drag the smoothed line toward itself for several seconds afterwards and book a
 * 400 m round trip of distance, which is exactly the failure the phase 4 acceptance
 * criterion tests for.
 *
 * `seq` still advances, because `seq` numbers rows in the track and the outlier is a row.
 *
 * ## Moving time
 *
 * Accrues between consecutive accepted fixes while the chain reports
 * [MotionState.MOVING]. Two intervals are excluded: one spanning a segment boundary, since
 * nobody observed what happened during the gap, and one ending in a pause. The second
 * means a pause is booked from the last moving fix rather than from the fix that detected
 * it — the detector needs a full window to decide, so by the time it fires, the device has
 * already been stationary for that window.
 *
 * Not thread-safe. One instance per track, driven by a single coroutine.
 */
public class FixPipeline(
    private val trackId: String,
    private val config: TrackingConfig,
    private val uuidGenerator: UuidV7Generator,
) {
    private val gate = ValidityGate(config)
    private val kalman = KalmanStage(config)
    private val accumulator = DistanceAccumulator(config)
    private val autoPause = AutoPauseDetector(config)

    private var seq = 0
    private var previousAcceptedNanos: Long? = null
    private var movingDurationMillis = 0L

    public val totalDistanceMetres: Double
        get() = accumulator.totalMetres

    public val totalMovingDurationMillis: Long
        get() = movingDurationMillis

    public val motionState: MotionState
        get() = autoPause.state

    public val segmentIndex: Int
        get() = kalman.segmentIndex

    /** Rows emitted so far, accepted and rejected alike. The next `seq` to be used. */
    public val pointCount: Int
        get() = seq

    /**
     * Forces the next accepted fix to open a new segment, without disturbing the totals.
     *
     * For a user-driven pause and resume. The clock cannot detect that boundary — the two
     * fixes either side of it may be seconds apart — but the enumerator may have travelled
     * between them, so the line must break and the distance must not be booked.
     */
    public fun startNewSegment() {
        kalman.startNewSegment()
        gate.reset()
        accumulator.startNewSegment()
        autoPause.reset()
        previousAcceptedNanos = null
    }

    public fun process(fix: RawFix): ProcessedFix {
        val previousNanos = previousAcceptedNanos

        return when (val verdict = gate.evaluate(fix)) {
            is FixVerdict.Rejected -> rejected(fix, verdict.reason)
            FixVerdict.Accepted -> accepted(fix, previousNanos)
        }
    }

    private fun rejected(
        fix: RawFix,
        reason: RejectionReason,
    ): ProcessedFix = ProcessedFix(
        point = fix.toTrackPoint(segmentIndex = kalman.segmentIndex, isOutlier = true),
        rejectionReason = reason,
        stepMetres = 0.0,
        totalDistanceMetres = accumulator.totalMetres,
        movingDurationMillis = movingDurationMillis,
        motionState = autoPause.state,
        startedNewSegment = false,
    )

    private fun accepted(
        fix: RawFix,
        previousAcceptedNanos: Long?,
    ): ProcessedFix {
        val smoothed = kalman.process(fix)

        if (smoothed.startedNewSegment) {
            // The gap already broke the line; the distance anchor and the motion window
            // both describe a device that is no longer where they think it is.
            accumulator.startNewSegment()
            autoPause.reset()
        }

        val step = accumulator.accumulate(smoothed.position, fix.accuracyMetres)
        val motion =
            autoPause.update(
                filteredPosition = smoothed.position,
                elapsedRealtimeNanos = fix.elapsedRealtimeNanos,
                speedMetresPerSecond = fix.speedMetresPerSecond,
            )

        if (
            previousAcceptedNanos != null &&
            !smoothed.startedNewSegment &&
            motion == MotionState.MOVING
        ) {
            movingDurationMillis +=
                (fix.elapsedRealtimeNanos - previousAcceptedNanos) / NANOS_PER_MILLI
        }
        this.previousAcceptedNanos = fix.elapsedRealtimeNanos

        return ProcessedFix(
            point =
                fix.toTrackPoint(
                    segmentIndex = smoothed.segmentIndex,
                    isOutlier = false,
                    smoothed = smoothed,
                ),
            rejectionReason = null,
            stepMetres = step,
            totalDistanceMetres = accumulator.totalMetres,
            movingDurationMillis = movingDurationMillis,
            motionState = motion,
            startedNewSegment = smoothed.startedNewSegment,
        )
    }

    private fun RawFix.toTrackPoint(
        segmentIndex: Int,
        isOutlier: Boolean,
        smoothed: SmoothedFix? = null,
    ): TrackPoint = TrackPoint(
        id = uuidGenerator.generate(),
        trackId = trackId,
        segmentIndex = segmentIndex,
        seq = seq++,
        position = position,
        altitudeMeters = altitudeMetres,
        accuracyMeters = accuracyMetres,
        verticalAccuracyMeters = verticalAccuracyMetres,
        bearingDegrees = bearingDegrees,
        speedMetersPerSecond = speedMetresPerSecond,
        speedAccuracyMetersPerSecond = speedAccuracyMetresPerSecond,
        provider = provider,
        satellitesUsed = satellitesUsed,
        recordedAt = recordedAtMillis,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        isMock = isMock,
        filteredPosition = smoothed?.position,
        filterVariance = smoothed?.varianceSquareMetres,
        isOutlier = isOutlier,
    )

    /**
     * Resumes numbering an existing track.
     *
     * A restarted service must not begin at `seq = 0`: `(track_id, seq)` is unique, so the
     * whole batch would collide with the rows already written and be silently ignored. The
     * filter state is intentionally *not* restored — it lives in memory only, so the first
     * fix after a restart opens a new segment, which is the honest description of what
     * happened.
     */
    public fun resumeFrom(
        nextSeq: Int,
        distanceMetres: Double,
        movingMillis: Long,
        segmentIndex: Int,
    ) {
        require(nextSeq >= 0) { "nextSeq must not be negative, was $nextSeq" }
        seq = nextSeq
        movingDurationMillis = movingMillis
        kalman.resumeAtSegment(segmentIndex)
        accumulator.resumeAt(distanceMetres)
        previousAcceptedNanos = null
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
