package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.WaypointDraftEntity

@Dao
public interface WaypointDraftDao {
    @Query("SELECT * FROM waypoint_draft WHERE project_id = :projectId")
    public suspend fun getDraft(projectId: String): WaypointDraftEntity?

    @Upsert
    public suspend fun upsertDraft(draft: WaypointDraftEntity)

    @Query("DELETE FROM waypoint_draft WHERE project_id = :projectId")
    public suspend fun deleteDraft(projectId: String)
}
