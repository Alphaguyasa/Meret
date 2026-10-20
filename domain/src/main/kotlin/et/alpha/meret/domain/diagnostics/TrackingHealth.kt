package et.alpha.meret.domain.diagnostics

/**
 * One entry in the tracking-gap log: a stretch of recording with no fix for longer than
 * three sampling intervals. Times are `elapsedRealtimeNanos`, never wall clock.
 *
 * [isOpen] while no fix has arrived yet; [durationMillis] is then the length so far.
 */
public data class GapRecord(
    public val startElapsedNanos: Long,
    public val durationMillis: Long,
    public val isOpen: Boolean,
)

/**
 * How well the current recording is receiving fixes, for the diagnostics screen.
 *
 * Counts, accuracies and durations only. There is deliberately no position here: the
 * diagnostics report is pasted into WhatsApp, and a fix would say where a respondent lives.
 */
public data class TrackingHealth(
    public val isRecording: Boolean = false,
    /** The sampling interval the window is judged against, or null before any recording. */
    public val samplingIntervalMillis: Long? = null,
    /** Fixes expected over the last ten minutes of recording time. */
    public val windowExpected: Int = 0,
    /** Fixes received over the same window. */
    public val windowActual: Int = 0,
    /** Fixes received since this process started recording. */
    public val fixCount: Int = 0,
    public val lastFixElapsedNanos: Long? = null,
    public val lastAccuracyMetres: Double? = null,
    public val lastSatellitesUsed: Int? = null,
    /** Oldest first, at most [TrackingHealthTracker.MAX_GAPS]. */
    public val gaps: List<GapRecord> = emptyList(),
) {
    /** `windowActual / windowExpected`, or null before a whole interval has been recorded. */
    public val windowRatio: Double?
        get() = if (windowExpected == 0) null else windowActual.toDouble() / windowExpected
}
