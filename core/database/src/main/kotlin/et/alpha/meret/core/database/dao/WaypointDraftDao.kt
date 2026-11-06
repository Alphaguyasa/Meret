package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.WaypointDraftEntity

@Dao
public interface WaypointDraftDao {
    @Query("SELECT * FROM waypoint_draft WHERE project_id = :projectId")
    public suspend fun getDraft(projectId: String): WaypointDraftEntity?

    /**
     * Whether any project's draft answers contain [text]. [text] must hold no `%` or `_`: the
     * orphan photo sweep passes a photo id (hex and dashes) to ask whether a draft names it.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM waypoint_draft WHERE form_data LIKE '%' || :text || '%')")
    public suspend fun anyDraftAnswersContain(text: String): Boolean

    @Upsert
    public suspend fun upsertDraft(draft: WaypointDraftEntity)

    @Query("DELETE FROM waypoint_draft WHERE project_id = :projectId")
    public suspend fun deleteDraft(projectId: String)
}
