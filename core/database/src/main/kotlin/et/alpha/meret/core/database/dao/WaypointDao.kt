package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.database.entity.WaypointMarkerRow
import et.alpha.meret.core.database.entity.WaypointPhotoEntity
import et.alpha.meret.core.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
public interface WaypointDao {
    /**
     * Every waypoint in [projectId], newest first. Ties on the wall clock fall back to the id,
     * which is time-ordered too.
     */
    @Query(
        "SELECT * FROM waypoint WHERE project_id = :projectId ORDER BY recorded_at DESC, id DESC",
    )
    public fun observeWaypoints(projectId: String): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoint WHERE track_id = :trackId ORDER BY recorded_at ASC")
    public fun observeWaypointsForTrack(trackId: String): Flow<List<WaypointEntity>>

    /**
     * Everything inside a map viewport. The bounds are compared numerically, which is
     * correct for every deployment area this app targets and wrong across the
     * antimeridian — the same simplification `BoundingBox` documents.
     */
    @Query(
        """
        SELECT * FROM waypoint
        WHERE project_id = :projectId
          AND lat BETWEEN :minLatitude AND :maxLatitude
          AND lon BETWEEN :minLongitude AND :maxLongitude
        ORDER BY recorded_at DESC
        """,
    )
    public fun observeWaypointsInBounds(
        projectId: String,
        minLatitude: Double,
        minLongitude: Double,
        maxLatitude: Double,
        maxLongitude: Double,
    ): Flow<List<WaypointEntity>>

    /**
     * What the map draws of each waypoint in [projectId], newest first and at most [limit]:
     * no title, notes or form data, so thousands of markers stay small. Ties on the wall
     * clock fall back to the id, which is time-ordered too.
     */
    @Query(
        """
        SELECT id, lat, lon, category FROM waypoint
        WHERE project_id = :projectId
        ORDER BY recorded_at DESC, id DESC
        LIMIT :limit
        """,
    )
    public fun observeWaypointMarkers(
        projectId: String,
        limit: Int,
    ): Flow<List<WaypointMarkerRow>>

    @Query("SELECT * FROM waypoint WHERE id = :waypointId")
    public fun observeWaypoint(waypointId: String): Flow<WaypointEntity?>

    @Query("SELECT * FROM waypoint WHERE id = :waypointId")
    public suspend fun getWaypoint(waypointId: String): WaypointEntity?

    @Query("SELECT * FROM waypoint WHERE sync_state = :syncState ORDER BY recorded_at ASC")
    public suspend fun getWaypointsBySyncState(syncState: SyncState): List<WaypointEntity>

    /**
     * Whether any saved waypoint's answers contain [text]. [text] must hold no `%` or `_`: the
     * orphan photo sweep passes a photo id (hex and dashes). A full scan, so only for the few
     * files no photo row names.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM waypoint WHERE form_data LIKE '%' || :text || '%')")
    public suspend fun anyWaypointAnswersContain(text: String): Boolean

    @Upsert
    public suspend fun upsertWaypoint(waypoint: WaypointEntity)

    @Upsert
    public suspend fun upsertWaypoints(waypoints: List<WaypointEntity>)

    @Upsert
    public suspend fun upsertWaypointPhotos(photos: List<WaypointPhotoEntity>)

    /**
     * [waypoint] and its [photos] in one transaction: a crash between them leaves neither, so
     * a waypoint is never in Room without the photos its answers name.
     */
    @Transaction
    public suspend fun upsertWaypointWithPhotos(
        waypoint: WaypointEntity,
        photos: List<WaypointPhotoEntity>,
    ) {
        upsertWaypoint(waypoint)
        if (photos.isNotEmpty()) upsertWaypointPhotos(photos)
    }

    @Query("UPDATE waypoint SET sync_state = :syncState WHERE id = :waypointId")
    public suspend fun updateSyncState(
        waypointId: String,
        syncState: SyncState,
    )

    /** Cascades to `waypoint_photo`. Photo files on disk are the caller's problem. */
    @Query("DELETE FROM waypoint WHERE id = :waypointId")
    public suspend fun deleteWaypoint(waypointId: String)
}
