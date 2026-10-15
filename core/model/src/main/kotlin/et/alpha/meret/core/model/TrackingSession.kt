package et.alpha.meret.core.model

/**
 * The recording in progress on this device, as the tracking service needs it to survive
 * process death (CLAUDE.md invariant 7).
 *
 * This is deliberately *not* the [Track] row. The row lives in Room and holds the data; this
 * is the small amount of runtime state a restarted service needs to pick up the same track
 * instead of opening a new one: which track, which segment it was writing, and whether the
 * user had paused it.
 *
 * Both clocks are kept, for the same reason every fix keeps both: [startedAtMillis] is for
 * display and is wrong on many field devices, [startedAtElapsedRealtimeNanos] is monotonic and
 * right. The elapsed-realtime value is only meaningful within one boot — it restarts from zero
 * when the phone does — so after a reboot it must be treated as unknown, never compared
 * against the new boot's clock.
 */
public data class TrackingSession(
    public val trackId: String,
    public val segmentIndex: Int = 0,
    public val isPaused: Boolean = false,
    public val startedAtMillis: Long,
    public val startedAtElapsedRealtimeNanos: Long,
) {
    init {
        require(trackId.isNotBlank()) { "trackId must not be blank" }
        require(segmentIndex >= 0) { "segmentIndex must not be negative, was $segmentIndex" }
        require(startedAtElapsedRealtimeNanos >= 0L) {
            "startedAtElapsedRealtimeNanos must not be negative, was $startedAtElapsedRealtimeNanos"
        }
    }
}
