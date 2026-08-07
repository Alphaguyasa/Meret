package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.ConflictResolution
import et.alpha.meret.core.model.OutboxEntityType

/**
 * A write the server rejected on a stale `base_version`, parked for a human.
 *
 * Both payloads are kept verbatim and nothing is auto-resolved. Picking a winner by
 * timestamp is not available to us: CLAUDE.md's device-clock warning applies here too, and
 * an enumerator's observation and a supervisor's correction are both real.
 */
@Entity(
    tableName = "sync_conflict",
    indices = [
        Index(value = ["entity_type", "entity_id"], name = "index_sync_conflict_entity"),
        // The unresolved-count badge reads this. Nulls are indexed in SQLite, so a partial
        // index is unnecessary.
        Index(value = ["resolved_at"], name = "index_sync_conflict_resolved_at"),
    ],
)
public data class SyncConflictEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "entity_type")
    public val entityType: OutboxEntityType,
    @ColumnInfo(name = "entity_id")
    public val entityId: String,
    @ColumnInfo(name = "local_payload")
    public val localPayload: String,
    @ColumnInfo(name = "remote_payload")
    public val remotePayload: String,
    @ColumnInfo(name = "detected_at")
    public val detectedAt: Long,
    @ColumnInfo(name = "resolved_at")
    public val resolvedAt: Long? = null,
    @ColumnInfo(name = "resolution")
    public val resolution: ConflictResolution? = null,
)
