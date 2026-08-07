package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.AssignmentStatus

/**
 * A polygon of ground allocated to one enumerator. Pull-only, with one exception:
 * [coveragePercent] is computed on the device from walked tracks, because coverage has to be
 * visible in the field with the network off. A delta pull must not clobber it.
 */
@Entity(
    tableName = "assignment",
    indices = [
        Index(value = ["project_id"], name = "index_assignment_project"),
        Index(value = ["assigned_user_id"], name = "index_assignment_user"),
    ],
)
public data class AssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "assigned_user_id")
    public val assignedUserId: String,
    @ColumnInfo(name = "name")
    public val name: String,
    @ColumnInfo(name = "polygon_geojson")
    public val polygonGeoJson: String,
    @ColumnInfo(name = "due_at")
    public val dueAt: Long? = null,
    @ColumnInfo(name = "status")
    public val status: AssignmentStatus = AssignmentStatus.OPEN,
    /** Computed locally from walked tracks, never pulled. */
    @ColumnInfo(name = "coverage_pct")
    public val coveragePercent: Double = 0.0,
)
