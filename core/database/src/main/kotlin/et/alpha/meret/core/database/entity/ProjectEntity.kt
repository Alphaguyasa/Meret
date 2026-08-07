package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Server-authoritative and pull-only. [updatedAt] is the server's clock and doubles as the
 * delta-pull cursor, so it is never written from the device.
 */
@Entity(tableName = "project")
public data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "org_id")
    public val orgId: String,
    @ColumnInfo(name = "name")
    public val name: String,
    @ColumnInfo(name = "description")
    public val description: String? = null,
    @ColumnInfo(name = "default_form_template_id")
    public val defaultFormTemplateId: String? = null,
    @ColumnInfo(name = "tile_pack_id")
    public val tilePackId: String? = null,
    @ColumnInfo(name = "starts_at")
    public val startsAt: Long? = null,
    @ColumnInfo(name = "ends_at")
    public val endsAt: Long? = null,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
    @ColumnInfo(name = "is_archived")
    public val isArchived: Boolean = false,
)
