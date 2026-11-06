package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.WaypointPhotoEntity
import et.alpha.meret.core.model.PhotoUploadState
import kotlinx.coroutines.flow.Flow

@Dao
public interface WaypointPhotoDao {
    @Query("SELECT * FROM waypoint_photo WHERE waypoint_id = :waypointId ORDER BY captured_at ASC")
    public fun observePhotos(waypointId: String): Flow<List<WaypointPhotoEntity>>

    @Query("SELECT * FROM waypoint_photo WHERE id = :photoId")
    public suspend fun getPhoto(photoId: String): WaypointPhotoEntity?

    /** Dedup key for peer sync: the same bytes arriving again are not a new photo. */
    @Query("SELECT * FROM waypoint_photo WHERE sha256 = :sha256 LIMIT 1")
    public suspend fun getPhotoBySha256(sha256: String): WaypointPhotoEntity?

    @Query(
        """
        SELECT * FROM waypoint_photo
        WHERE upload_state = :uploadState
        ORDER BY captured_at ASC
        LIMIT :limit
        """,
    )
    public suspend fun getPhotosByUploadState(
        uploadState: PhotoUploadState,
        limit: Int,
    ): List<WaypointPhotoEntity>

    /** Every photo row's id: the files the orphan sweep must never touch. */
    @Query("SELECT id FROM waypoint_photo")
    public suspend fun getPhotoIds(): List<String>

    /** Every photo row's file, in case a row ever names a file not called after its id. */
    @Query("SELECT local_path FROM waypoint_photo")
    public suspend fun getPhotoLocalPaths(): List<String>

    @Query("SELECT COUNT(*) FROM waypoint_photo WHERE upload_state != 'uploaded'")
    public fun observeOutstandingUploadCount(): Flow<Int>

    @Upsert
    public suspend fun upsertPhoto(photo: WaypointPhotoEntity)

    @Upsert
    public suspend fun upsertPhotos(photos: List<WaypointPhotoEntity>)

    @Query(
        """
        UPDATE waypoint_photo
        SET upload_state = :uploadState,
            remote_path = :remotePath,
            upload_attempts = upload_attempts + :attemptIncrement
        WHERE id = :photoId
        """,
    )
    public suspend fun updateUploadState(
        photoId: String,
        uploadState: PhotoUploadState,
        remotePath: String?,
        attemptIncrement: Int,
    )

    @Query("DELETE FROM waypoint_photo WHERE id = :photoId")
    public suspend fun deletePhoto(photoId: String)
}
