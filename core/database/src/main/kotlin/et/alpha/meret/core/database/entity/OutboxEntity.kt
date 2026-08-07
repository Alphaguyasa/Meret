package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.OutboxEntityType
import et.alpha.meret.core.model.OutboxOperation

/**
 * One pending write, serialised at enqueue time into exactly the bytes that go on the wire.
 *
 * The composite index mirrors the drain query verbatim — `where next_attempt_at <= ? order
 * by priority, created_at` — so the worker never scans rows whose backoff has not elapsed.
 * Leading with `next_attempt_at` is what makes that a range scan on a table that, after a
 * day offline, is the largest thing the sync path touches.
 *
 * No foreign key to the entity this row describes. The outbox has to survive its subject:
 * a delete is queued precisely when the local row is going away, and a peer may forward a
 * payload for an entity this device never held.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(
            value = ["next_attempt_at", "priority", "created_at"],
            name = "index_outbox_drain",
        ),
        Index(value = ["entity_type", "entity_id"], name = "index_outbox_entity"),
    ],
)
public data class OutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "entity_type")
    public val entityType: OutboxEntityType,
    @ColumnInfo(name = "entity_id")
    public val entityId: String,
    @ColumnInfo(name = "op")
    public val operation: OutboxOperation,
    @ColumnInfo(name = "payload")
    public val payload: String,
    /** Peer-sync digest: two devices agree on this without exchanging the payload. */
    @ColumnInfo(name = "payload_sha256")
    public val payloadSha256: String,
    /** Lower drains first. Tracks before waypoints. */
    @ColumnInfo(name = "priority")
    public val priority: Int,
    @ColumnInfo(name = "attempts")
    public val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    public val lastError: String? = null,
    @ColumnInfo(name = "next_attempt_at")
    public val nextAttemptAt: Long,
    @ColumnInfo(name = "created_at")
    public val createdAt: Long,
)
