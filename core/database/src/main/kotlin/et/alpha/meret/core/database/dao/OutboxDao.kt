package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.OutboxEntity
import et.alpha.meret.core.model.OutboxEntityType
import kotlinx.coroutines.flow.Flow

@Dao
public interface OutboxDao {
    /**
     * One drain batch, in exactly the order `index_outbox_drain` stores.
     *
     * `next_attempt_at <= :now` leads because it is the selective predicate after a day
     * offline: most rows are waiting out a backoff, and none of them should be read.
     */
    @Query(
        """
        SELECT * FROM outbox
        WHERE next_attempt_at <= :now
        ORDER BY priority ASC, created_at ASC
        LIMIT :limit
        """,
    )
    public suspend fun getDueEntries(
        now: Long,
        limit: Int,
    ): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    public fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox WHERE entity_type = :entityType")
    public suspend fun countByType(entityType: OutboxEntityType): Int

    @Query("SELECT * FROM outbox WHERE entity_type = :entityType AND entity_id = :entityId")
    public suspend fun getEntriesFor(
        entityType: OutboxEntityType,
        entityId: String,
    ): List<OutboxEntity>

    /** The peer-sync digest: every hash this device is holding, without the payloads. */
    @Query("SELECT payload_sha256 FROM outbox")
    public suspend fun getAllDigests(): List<String>

    @Upsert
    public suspend fun upsertEntry(entry: OutboxEntity)

    @Upsert
    public suspend fun upsertEntries(entries: List<OutboxEntity>)

    /**
     * Backoff bookkeeping after a transient failure. The caller computes
     * `next_attempt_at`; the exponent and the jitter are sync policy, not storage.
     */
    @Query(
        """
        UPDATE outbox
        SET attempts = attempts + 1,
            last_error = :error,
            next_attempt_at = :nextAttemptAt
        WHERE id = :entryId
        """,
    )
    public suspend fun recordFailure(
        entryId: String,
        error: String?,
        nextAttemptAt: Long,
    )

    /** Called on 2xx. The row's absence is the record that the write landed. */
    @Query("DELETE FROM outbox WHERE id = :entryId")
    public suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM outbox WHERE id IN (:entryIds)")
    public suspend fun deleteEntries(entryIds: List<String>)
}
