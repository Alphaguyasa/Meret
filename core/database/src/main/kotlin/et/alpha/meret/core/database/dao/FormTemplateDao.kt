package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.FormTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface FormTemplateDao {
    @Query("SELECT * FROM form_template WHERE project_id = :projectId ORDER BY name ASC")
    public fun observeTemplates(projectId: String): Flow<List<FormTemplateEntity>>

    @Query("SELECT * FROM form_template WHERE id = :templateId")
    public suspend fun getTemplate(templateId: String): FormTemplateEntity?

    /**
     * The exact version a waypoint was captured against. An old submission has to keep
     * rendering after the template moves on, so this never falls back to the newest.
     */
    @Query("SELECT * FROM form_template WHERE id = :templateId AND version = :version")
    public suspend fun getTemplateVersion(
        templateId: String,
        version: Int,
    ): FormTemplateEntity?

    @Query("SELECT MAX(updated_at) FROM form_template")
    public suspend fun getLatestUpdatedAt(): Long?

    @Upsert
    public suspend fun upsertTemplates(templates: List<FormTemplateEntity>)

    @Query("DELETE FROM form_template WHERE id = :templateId")
    public suspend fun deleteTemplate(templateId: String)
}
