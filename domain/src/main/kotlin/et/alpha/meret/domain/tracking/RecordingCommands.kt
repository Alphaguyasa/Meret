package et.alpha.meret.domain.tracking

import et.alpha.meret.core.common.result.AppResult

/**
 * Commands to the recording's foreground service, which alone drives the tracking controller
 * (`ARCHITECTURE.md` ADR-009). Implemented by `:feature:tracking`, so a screen in another
 * feature can record without seeing the service.
 *
 * Each call only hands the service an intent; the recording state changes when the service
 * has run it.
 */
public interface RecordingCommands {
    /**
     * Starts recording into [trackId], whose row must exist. Call only from a user action:
     * Android 12+ refuses a foreground service started from the background, and that refusal
     * is the failure returned here.
     */
    public fun start(trackId: String): AppResult<Unit>

    public fun pause()

    public fun resume()

    public fun stop()

    /**
     * Records into [trackId] again, just after the user stopped it by mistake; the recording
     * continues in a new segment. A user action only, like [start].
     */
    public fun reopen(trackId: String): AppResult<Unit>
}
