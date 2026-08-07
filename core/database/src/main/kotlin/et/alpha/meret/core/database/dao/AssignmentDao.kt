package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.AssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface AssignmentDao {
    @Query("SELECT * FROM assignment WHERE project_id = :projectId ORDER BY due_at ASC, name ASC")
    public fun observeAssignments(projectId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignment WHERE assigned_user_id = :userId ORDER BY due_at ASC")
    public fun observeAssignmentsForUser(userId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignment WHERE id = :assignmentId")
    public fun observeAssignment(assignmentId: String): Flow<AssignmentEntity?>

    @Query("SELECT * FROM assignment WHERE id = :assignmentId")
    public suspend fun getAssignment(assignmentId: String): AssignmentEntity?

    /**
     * Upserting a pulled assignment would reset [AssignmentEntity.coveragePercent], which is
     * computed on the device and exists nowhere else. Callers pulling from the server must
     * carry the local value forward; this is the only writer of it.
     */
    @Query("UPDATE assignment SET coverage_pct = :coveragePercent WHERE id = :assignmentId")
    public suspend fun updateCoverage(
        assignmentId: String,
        coveragePercent: Double,
    )

    @Upsert
    public suspend fun upsertAssignments(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignment WHERE id = :assignmentId")
    public suspend fun deleteAssignment(assignmentId: String)
}
