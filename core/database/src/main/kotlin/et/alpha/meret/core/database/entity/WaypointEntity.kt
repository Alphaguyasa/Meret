package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.SyncState

/**
 * A geotagged observation, optionally attached to the track that was recording at the time.
 *
 * No foreign key on either [projectId] or [trackId]. The project reasoning is the same as
 * [TrackEntity]'s; [trackId] is left free so that discarding a track never silently takes
 * the waypoints captured during it, which are independently valuable submissions.
 *
 * [formData] stays JSON rather than exploding into columns: form schemas are per-project and
 * pull-only, so the app has to accept a template it has never seen without a migration.
 */
@Entity(
    tableName = "waypoint",
    indices = [
        Index(value = ["project_id"], name = "index_waypoint_project"),
        Index(value = ["track_id"], name = "index_waypoint_track"),
        Index(value = ["sync_state"], name = "index_waypoint_sync_state"),
    ],
)
public data class WaypointEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "track_id")
    public val trackId: String? = null,
    @ColumnInfo(name = "lat")
    public val latitude: Double,
    @ColumnInfo(name = "lon")
    public val longitude: Double,
    @ColumnInfo(name = "accuracy_m")
    public val accuracyMeters: Double,
    @ColumnInfo(name = "altitude_m")
    public val altitudeMeters: Double? = null,
    @ColumnInfo(name = "recorded_at")
    public val recordedAt: Long,
    @ColumnInfo(name = "category")
    public val category: String? = null,
    @ColumnInfo(name = "title")
    public val title: String? = null,
    @ColumnInfo(name = "notes")
    public val notes: String? = null,
    @ColumnInfo(name = "form_template_id")
    public val formTemplateId: String? = null,
    @ColumnInfo(name = "form_version")
    public val formVersion: Int? = null,
    @ColumnInfo(name = "form_data")
    public val formData: String? = null,
    /** The version this edit was made against. The server compares it rather than overwriting. */
    @ColumnInfo(name = "base_version")
    public val baseVersion: Int = 0,
    @ColumnInfo(name = "sync_state")
    public val syncState: SyncState = SyncState.PENDING,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
)
