package et.alpha.meret.core.model

/** What kind of thing an outbox row carries. Determines which endpoint drains it. */
public enum class OutboxEntityType(
    override val wireName: String,
) : WireNamed {
    TRACK("track"),

    /** A chunk of points, not a single fix. Points never queue individually. */
    TRACK_POINTS("track_points"),
    WAYPOINT("waypoint"),
    PHOTO("photo"),
    MEASUREMENT("measurement"),
}

public enum class OutboxOperation(
    override val wireName: String,
) : WireNamed {
    UPSERT("upsert"),
    DELETE("delete"),
}

/**
 * One pending write, already serialised into exactly the bytes that go on the wire.
 *
 * The payload is captured at enqueue time rather than re-read from the tables at send
 * time. That is what makes a retry send what the user actually did, not what the row
 * happens to look like after three later edits, and it is what lets a peer forward a
 * write it cannot itself interpret.
 *
 * [payloadSha256] is the peer-sync digest: two devices holding the same write agree on
 * this hash without exchanging the payload.
 *
 * [priority] drains low-first. Tracks go before waypoints so that a supervisor watching a
 * partial upload sees where someone walked before seeing what they recorded.
 */
public data class OutboxEntry(
    public val id: String,
    public val entityType: OutboxEntityType,
    public val entityId: String,
    public val operation: OutboxOperation,
    public val payload: String,
    public val payloadSha256: String,
    public val priority: Int = DEFAULT_PRIORITY,
    public val attempts: Int = 0,
    public val lastError: String? = null,
    public val nextAttemptAt: Long,
    public val createdAt: Long,
) {
    /** True when the backoff has elapsed and a worker may claim this row. */
    public fun isDue(now: Long): Boolean = nextAttemptAt <= now

    public companion object {
        public const val DEFAULT_PRIORITY: Int = 100
    }
}
