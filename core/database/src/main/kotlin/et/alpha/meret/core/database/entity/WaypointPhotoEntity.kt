package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.PhotoUploadState

/**
 * A photo attached to a waypoint.
 *
 * Cascades from its waypoint: unlike a waypoint's relationship to a track, a photo has no
 * meaning without the observation it documents, and both rows are written by the same device
 * in the same transaction, so the parent is always present.
 *
 * Photos carry [uploadState] rather than riding the waypoint's sync state because they
 * upload on a separate, unmetered-only worker — a waypoint is usefully synced long before
 * its photos are.
 */
@Entity(
    tableName = "waypoint_photo",
    foreignKeys = [
        ForeignKey(
            entity = WaypointEntity::class,
            parentColumns = ["id"],
            childColumns = ["waypoint_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["waypoint_id"], name = "index_waypoint_photo_waypoint"),
        Index(value = ["upload_state"], name = "index_waypoint_photo_upload_state"),
        // Dedup on content: the same file arriving again from a peer is not a new photo.
        Index(value = ["sha256"], name = "index_waypoint_photo_sha256"),
    ],
)
public data class WaypointPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "waypoint_id")
    public val waypointId: String,
    @ColumnInfo(name = "local_path")
    public val localPath: String,
    @ColumnInfo(name = "remote_path")
    public val remotePath: String? = null,
    /** Dimensions of the compressed file on disk, not of the capture. */
    @ColumnInfo(name = "width")
    public val width: Int,
    @ColumnInfo(name = "height")
    public val height: Int,
    @ColumnInfo(name = "bytes")
    public val bytes: Long,
    /** Integrity check and peer-sync dedup key. */
    @ColumnInfo(name = "sha256")
    public val sha256: String,
    @ColumnInfo(name = "captured_at")
    public val capturedAt: Long,
    @ColumnInfo(name = "lat")
    public val latitude: Double? = null,
    @ColumnInfo(name = "lon")
    public val longitude: Double? = null,
    @ColumnInfo(name = "upload_state")
    public val uploadState: PhotoUploadState = PhotoUploadState.PENDING,
    @ColumnInfo(name = "upload_attempts")
    public val uploadAttempts: Int = 0,
)
