package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A versioned waypoint form definition. Pull-only.
 *
 * Old versions are retained rather than replaced: a waypoint records the version it was
 * captured against, and deleting the template it referenced would leave a submission that
 * can be synced but never rendered.
 */
@Entity(
    tableName = "form_template",
    indices = [
        Index(value = ["project_id"], name = "index_form_template_project"),
    ],
)
public data class FormTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "version")
    public val version: Int,
    @ColumnInfo(name = "name")
    public val name: String,
    /** Interpreted by the renderer at runtime. The app ships no compiled knowledge of it. */
    @ColumnInfo(name = "schema_json")
    public val schemaJson: String,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
)
