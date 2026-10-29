package et.alpha.meret.domain.tracking

import et.alpha.meret.core.common.result.AppResult

/** Writes the row a new recording goes into. Room, through `:core:data`. */
public interface NewTrackStore {
    /** Writes a new, empty track row and returns its id (a UUIDv7). */
    public suspend fun create(): AppResult<String>

    /**
     * Deletes [trackId] if it is still empty: for a row whose recording never started. A
     * track with any point is kept, whatever the caller thinks.
     */
    public suspend fun discardIfEmpty(trackId: String)
}
