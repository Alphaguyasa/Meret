package et.alpha.meret.core.data.tracking

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What the tracking subsystem is doing right now. */
public sealed interface TrackingState {
    /** No track is being recorded. */
    public data object Idle : TrackingState

    /**
     * [trackId] is being recorded. While [isPaused] the engine is stopped and nothing is
     * written; the track and its session stay open.
     */
    public data class Recording(
        public val trackId: String,
        public val isPaused: Boolean,
    ) : TrackingState
}

/**
 * The one owner of a recording: engine, recorder, persisted session and track status, kept in
 * step with each other.
 *
 * `ARCHITECTURE.md` ADR-009: the foreground service is the only caller. It turns intents and
 * notification actions into these calls and holds the wakelock while [state] is an unpaused
 * [TrackingState.Recording]. Everything here is plain suspend code, so the rules that keep a
 * track alive across process death are tested without a service.
 *
 * Commands are serialised: a Stop from the notification racing a Pause from the screen runs
 * one after the other, never interleaved.
 */
public interface TrackingController {
    public val state: StateFlow<TrackingState>

    /** The track being recorded, if any, as Room has it. For the live notification. */
    public val activeTrack: Flow<Track?>

    /**
     * The line of the track being recorded, simplified, at most once per two seconds; null
     * while idle. For the map. Raw points are read, never changed.
     */
    public val activeLine: Flow<TrackLine?>

    /** How many segments the track being recorded has so far; null while idle. */
    public val activeSegmentCount: Flow<Int?>

    /**
     * Fix rate, last accuracy and the gap log of the recording in this process, for the
     * diagnostics screen. Starts empty after process death; the persisted track does not
     * carry it.
     */
    public val health: StateFlow<TrackingHealth>

    /**
     * Starts recording into [trackId], whose row must already exist.
     *
     * Already recording [trackId] is a success and changes nothing; recording another track
     * is a [et.alpha.meret.core.common.result.AppError.Conflict]. A missing row is
     * [et.alpha.meret.core.common.result.AppError.Storage]. If location permission has been
     * revoked the track is opened paused and the result is
     * [et.alpha.meret.core.common.result.AppError.Permission].
     */
    public suspend fun start(trackId: String): AppResult<Unit>

    /**
     * Picks up the session persisted before the process died, if there is one, and returns
     * the resulting state. A session whose track is gone or already finished is cleared.
     */
    public suspend fun restore(): AppResult<TrackingState>

    /**
     * The track an earlier process left open, if this one is not recording it: what a
     * reboot or a killed service leaves behind. Reads only; [restore] picks it up. A session
     * whose track is gone or already finished counts as none.
     */
    public suspend fun interruptedTrackId(): String?

    /** Stops the engine and writes the pending batch. No-op unless recording unpaused. */
    public suspend fun pause()

    /** Restarts the engine in a new segment. No-op unless paused. */
    public suspend fun resume(): AppResult<Unit>

    /** Stops the engine, writes everything received, completes the track, ends the session. */
    public suspend fun stop()

    /**
     * Undoes a stop made by mistake: [trackId] goes back to active, its end time cleared, and
     * recording continues into it in a new segment.
     *
     * Only the track this process stopped last, and only while nothing records:
     * otherwise a [et.alpha.meret.core.common.result.AppError.Conflict]. A track that is no
     * longer completed (deleted since) is
     * [et.alpha.meret.core.common.result.AppError.Storage]. A revoked permission leaves the
     * reopened track paused, as [start] does.
     */
    public suspend fun reopen(trackId: String): AppResult<Unit>
}
