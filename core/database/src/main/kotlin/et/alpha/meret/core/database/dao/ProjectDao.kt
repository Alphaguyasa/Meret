package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProjectDao {
    @Query("SELECT * FROM project WHERE is_archived = 0 ORDER BY name ASC")
    public fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project ORDER BY name ASC")
    public fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project WHERE id = :projectId")
    public fun observeProject(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = :projectId")
    public suspend fun getProject(projectId: String): ProjectEntity?

    /** The delta-pull cursor: the newest server clock this device has seen for this table. */
    @Query("SELECT MAX(updated_at) FROM project")
    public suspend fun getLatestUpdatedAt(): Long?

    @Upsert
    public suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Query("DELETE FROM project WHERE id = :projectId")
    public suspend fun deleteProject(projectId: String)
}
