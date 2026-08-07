package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.SyncConflictEntity
import et.alpha.meret.core.model.ConflictResolution
import kotlinx.coroutines.flow.Flow

@Dao
public interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflict WHERE resolved_at IS NULL ORDER BY detected_at ASC")
    public fun observeUnresolved(): Flow<List<SyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM sync_conflict WHERE resolved_at IS NULL")
    public fun observeUnresolvedCount(): Flow<Int>

    @Query("SELECT * FROM sync_conflict WHERE id = :conflictId")
    public suspend fun getConflict(conflictId: String): SyncConflictEntity?

    @Upsert
    public suspend fun upsertConflict(conflict: SyncConflictEntity)

    /**
     * Resolving keeps the row. Both payloads stay on the device as the record of what was
     * chosen and what was given up — a resolution nobody can audit is not a resolution.
     */
    @Query(
        """
        UPDATE sync_conflict
        SET resolution = :resolution, resolved_at = :resolvedAt
        WHERE id = :conflictId
        """,
    )
    public suspend fun resolve(
        conflictId: String,
        resolution: ConflictResolution,
        resolvedAt: Long,
    )

    @Query("DELETE FROM sync_conflict WHERE id = :conflictId")
    public suspend fun deleteConflict(conflictId: String)
}
