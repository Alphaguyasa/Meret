package et.alpha.meret.core.common.tracking

/**
 * A stretch of recording with no fix for longer than the gap threshold.
 *
 * Reported once per gap: either by [TrackingHealthMonitor.check] while it is still open
 * ([endElapsedNanos] null), or by [TrackingHealthMonitor.onFix] when the fix that closes it
 * arrives and it had not been reported yet. The `tracking_gap` diagnostics event.
 */
public data class TrackingGap(
    public val startElapsedNanos: Long,
    public val endElapsedNanos: Long?,
    public val durationMillis: Long,
)

/**
 * Fixes received against fixes expected over the trailing window, counting recording time
 * only. [expected] is recording time divided by the sampling interval, rounded down.
 */
public data class WindowHealth(
    public val expected: Int,
    public val actual: Int,
) {
    /** `actual / expected`, or null before a whole interval has been recorded. */
    public val ratio: Double?
        get() = if (expected == 0) null else actual.toDouble() / expected
}

/**
 * Watches the fix timeline of a recording for the failure an OEM battery killer produces:
 * the service is alive but fixes stop, or arrive in sparse clumps.
 *
 * Pure bookkeeping on `elapsedRealtimeNanos` (never wall clock, see `TimeProvider`). The
 * caller feeds it recording transitions and fixes, and polls [check] and [windowHealth].
 *
 * ## Pauses do not count
 *
 * Time while paused is neither a gap nor expected fixes: the engine is deliberately off. On
 * [resume] the gap clock restarts from the resume, not from the last fix before the pause.
 *
 * Not thread-safe: drive it from one coroutine.
 */
public class TrackingHealthMonitor(
    samplingIntervalMillis: Long,
    windowMillis: Long = DEFAULT_WINDOW_MS,
    gapFactor: Double = DEFAULT_GAP_FACTOR,
) {
    init {
        require(samplingIntervalMillis > 0) {
            "samplingIntervalMillis must be positive, was $samplingIntervalMillis"
        }
        require(windowMillis > 0) { "windowMillis must be positive, was $windowMillis" }
        require(gapFactor >= 1.0) { "gapFactor must be at least 1, was $gapFactor" }
    }

    private val intervalNanos = samplingIntervalMillis * NANOS_PER_MILLI
    private val windowNanos = windowMillis * NANOS_PER_MILLI
    private val gapThresholdNanos = (intervalNanos * gapFactor).toLong()

    /** Start of the stretch being watched for a gap: the last fix, or the (re)start. */
    private var referenceNanos = 0L
    private var gapReported = false
    private var recordingSinceNanos: Long? = null

    /** Closed recording intervals still overlapping the window, oldest first. */
    private val pastRecording = ArrayDeque<LongRange>()
    private val fixTimes = ArrayDeque<Long>()

    public val isRecording: Boolean
        get() = recordingSinceNanos != null

    /** Recording starts or resumes at [atElapsedNanos]. No-op if already recording. */
    public fun resume(atElapsedNanos: Long) {
        if (isRecording) return
        recordingSinceNanos = atElapsedNanos
        referenceNanos = atElapsedNanos
        gapReported = false
    }

    /** Recording pauses or stops at [atElapsedNanos]. An open gap ends here unreported. */
    public fun pause(atElapsedNanos: Long) {
        val since = recordingSinceNanos ?: return
        pastRecording.addLast(since..atElapsedNanos)
        recordingSinceNanos = null
    }

    /**
     * Records a fix. Returns the gap it closes, if that gap is longer than the threshold and
     * [check] has not already reported it. Fixes while paused are ignored.
     */
    public fun onFix(atElapsedNanos: Long): TrackingGap? {
        if (!isRecording) return null
        fixTimes.addLast(atElapsedNanos)
        val gapNanos = atElapsedNanos - referenceNanos
        val closed =
            if (gapNanos > gapThresholdNanos && !gapReported) {
                TrackingGap(referenceNanos, atElapsedNanos, gapNanos / NANOS_PER_MILLI)
            } else {
                null
            }
        referenceNanos = atElapsedNanos
        gapReported = false
        return closed
    }

    /** Reports a gap that is still open at [nowElapsedNanos], once. */
    public fun check(nowElapsedNanos: Long): TrackingGap? {
        val gapNanos = nowElapsedNanos - referenceNanos
        if (!isRecording || gapReported || gapNanos <= gapThresholdNanos) return null
        gapReported = true
        return TrackingGap(referenceNanos, null, gapNanos / NANOS_PER_MILLI)
    }

    /** Expected and received fixes over the window ending at [nowElapsedNanos]. */
    public fun windowHealth(nowElapsedNanos: Long): WindowHealth {
        val windowStart = nowElapsedNanos - windowNanos
        while (fixTimes.isNotEmpty() && fixTimes.first() < windowStart) fixTimes.removeFirst()
        while (pastRecording.isNotEmpty() && pastRecording.first().last < windowStart) {
            pastRecording.removeFirst()
        }

        val open = recordingSinceNanos?.let { it..nowElapsedNanos }
        val recordingNanos =
            (pastRecording + listOfNotNull(open)).sumOf { range ->
                (minOf(range.last, nowElapsedNanos) - maxOf(range.first, windowStart))
                    .coerceAtLeast(0L)
            }
        return WindowHealth(
            expected = (recordingNanos / intervalNanos).toInt(),
            actual = fixTimes.count { it <= nowElapsedNanos },
        )
    }

    public companion object {
        /** BUILD_PLAN phase 5: health is judged per 10-minute window. */
        public const val DEFAULT_WINDOW_MS: Long = 10L * 60 * 1_000

        /** BUILD_PLAN phase 5: a gap is anything longer than 3× the sampling interval. */
        public const val DEFAULT_GAP_FACTOR: Double = 3.0

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
