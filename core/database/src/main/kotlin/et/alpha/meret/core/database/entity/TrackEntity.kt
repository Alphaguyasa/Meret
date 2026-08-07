package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TrackPurpose
import et.alpha.meret.core.model.TrackStatus

/**
 * One recording session.
 *
 * **No foreign key on [projectId], deliberately.** Projects are pulled from the server and
 * tracks are created offline; a device that has not yet pulled — or that had its project
 * list trimmed — must still be able to record. A foreign key here would turn a stale
 * projects table into a refusal to start tracking, which is exactly the class of failure
 * the offline-first invariant exists to prevent. Referential integrity for `project_id` is
 * the server's job, where the row is guaranteed to exist.
 *
 * The four `bbox_*` columns are all-or-nothing: null until the first accepted fix, then all
 * four populated together. The mapper enforces that pairing so no half-built rectangle ever
 * reaches the map.
 */
@Entity(
    tableName = "track",
    indices = [
        // The track list is always "this project, newest first". Matching the sort order
        // in the index keeps that query an index scan instead of a sort of every track.
        Index(
            value = ["project_id", "started_at"],
            orders = [Index.Order.ASC, Index.Order.DESC],
            name = "index_track_project_started",
        ),
        // The outbox drain and the sync badge both filter on this.
        Index(value = ["sync_state"], name = "index_track_sync_state"),
    ],
)
public data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "device_id")
    public val deviceId: String,
    @ColumnInfo(name = "user_id")
    public val userId: String,
    @ColumnInfo(name = "name")
    public val name: String? = null,
    @ColumnInfo(name = "started_at")
    public val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    public val endedAt: Long? = null,
    @ColumnInfo(name = "status")
    public val status: TrackStatus,
    @ColumnInfo(name = "point_count")
    public val pointCount: Int = 0,
    @ColumnInfo(name = "distance_m")
    public val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "duration_moving_ms")
    public val movingDurationMillis: Long = 0L,
    @ColumnInfo(name = "bbox_min_lat")
    public val bboxMinLatitude: Double? = null,
    @ColumnInfo(name = "bbox_min_lon")
    public val bboxMinLongitude: Double? = null,
    @ColumnInfo(name = "bbox_max_lat")
    public val bboxMaxLatitude: Double? = null,
    @ColumnInfo(name = "bbox_max_lon")
    public val bboxMaxLongitude: Double? = null,
    @ColumnInfo(name = "simplified_geojson")
    public val simplifiedGeoJson: String? = null,
    @ColumnInfo(name = "purpose")
    public val purpose: TrackPurpose? = null,
    @ColumnInfo(name = "notes")
    public val notes: String? = null,
    @ColumnInfo(name = "sync_state")
    public val syncState: SyncState = SyncState.PENDING,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
)
