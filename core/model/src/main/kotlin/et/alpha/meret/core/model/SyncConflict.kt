package et.alpha.meret.core.model

public enum class ConflictResolution(
    override val wireName: String,
) : WireNamed {
    /** Keep the device's version and re-push it against the server's current version. */
    LOCAL("local"),

    /** Accept the server's version and overwrite the local row. */
    REMOTE("remote"),

    /** A field-by-field merge the user assembled in the resolution UI. */
    MERGED("merged"),
}

/**
 * A write the server rejected because its `base_version` was stale, parked for a human.
 *
 * Both payloads are kept verbatim. Nothing is auto-resolved and nothing is discarded: an
 * enumerator's observation and a supervisor's correction are both real, and picking a
 * winner by timestamp on devices whose clocks are known to be wrong is how field data
 * quietly disappears.
 *
 * [resolvedAt] and [resolution] are null until someone decides.
 */
public data class SyncConflict(
    public val id: String,
    public val entityType: OutboxEntityType,
    public val entityId: String,
    public val localPayload: String,
    public val remotePayload: String,
    public val detectedAt: Long,
    public val resolvedAt: Long? = null,
    public val resolution: ConflictResolution? = null,
) {
    public val isResolved: Boolean
        get() = resolution != null
}
