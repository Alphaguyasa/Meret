package et.alpha.meret.core.model

/** Where a locally-owned row stands relative to the server. */
public enum class SyncState(
    override val wireName: String,
) : WireNamed {
    /** Written locally, queued in the outbox, not yet accepted by the server. */
    PENDING("pending"),

    /** Claimed by a sync worker and in flight. */
    SYNCING("syncing"),

    /** Accepted by the server. */
    SYNCED("synced"),

    /** The server rejected our `base_version`; the local copy is kept, never discarded. */
    CONFLICT("conflict"),
}
