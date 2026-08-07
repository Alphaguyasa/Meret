package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
public interface WaypointDao {
    @Query("SELECT * FROM waypoint WHERE project_id = :projectId ORDER BY recorded_at DESC")
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

    @Query("SELECT * FROM waypoint WHERE id = :waypointId")
    public fun observeWaypoint(waypointId: String): Flow<WaypointEntity?>

    @Query("SELECT * FROM waypoint WHERE id = :waypointId")
    public suspend fun getWaypoint(waypointId: String): WaypointEntity?

    @Query("SELECT * FROM waypoint WHERE sync_state = :syncState ORDER BY recorded_at ASC")
    public suspend fun getWaypointsBySyncState(syncState: SyncState): List<WaypointEntity>

    @Upsert
    public suspend fun upsertWaypoint(waypoint: WaypointEntity)

    @Upsert
    public suspend fun upsertWaypoints(waypoints: List<WaypointEntity>)

    @Query("UPDATE waypoint SET sync_state = :syncState WHERE id = :waypointId")
    public suspend fun updateSyncState(
        waypointId: String,
        syncState: SyncState,
    )

    /** Cascades to `waypoint_photo`. Photo files on disk are the caller's problem. */
    @Query("DELETE FROM waypoint WHERE id = :waypointId")
    public suspend fun deleteWaypoint(waypointId: String)
}
