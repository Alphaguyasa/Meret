package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackWithPoints
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.flow.Flow

@Dao
public interface TrackDao {
    /** Newest first, matching the order of `index_track_project_started`. */
    @Query("SELECT * FROM track WHERE project_id = :projectId ORDER BY started_at DESC")
    public fun observeTracks(projectId: String): Flow<List<TrackEntity>>

    /**
     * Every track on this device except discarded ones, newest first: the track list. Not per
     * project, because tracks recorded before sign-in have no real project yet.
     */
    @Query("SELECT * FROM track WHERE status != 'discarded' ORDER BY started_at DESC")
    public fun observeListedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE id = :trackId")
    public fun observeTrack(trackId: String): Flow<TrackEntity?>

    /**
     * The at-most-one track this device is recording into, across process death.
     *
     * Ordered so that a device left in a bad state by a kill mid-write surfaces the most
     * recent candidate rather than an arbitrary one.
     */
    @Query(
        """
        SELECT * FROM track
        WHERE status IN ('active', 'paused')
        ORDER BY started_at DESC
        LIMIT 1
        """,
    )
    public fun observeActiveTrack(): Flow<TrackEntity?>

    /**
     * The track plus every fix, in one transaction.
     *
     * BUILD_PLAN phase 3 holds this to 200 ms for a 20,000-point track. The cost is
     * dominated by the child query, which the `(track_id, seq)` index turns into a single
     * range scan.
     */
    @Transaction
    @Query("SELECT * FROM track WHERE id = :trackId")
    public fun observeTrackWithPoints(trackId: String): Flow<TrackWithPoints?>

    @Transaction
    @Query("SELECT * FROM track WHERE id = :trackId")
    public suspend fun getTrackWithPoints(trackId: String): TrackWithPoints?

    @Query("SELECT * FROM track WHERE id = :trackId")
    public suspend fun getTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM track WHERE sync_state = :syncState ORDER BY started_at ASC")
    public suspend fun getTracksBySyncState(syncState: SyncState): List<TrackEntity>

    @Upsert
    public suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    public suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE track SET status = :status, updated_at = :updatedAt WHERE id = :trackId")
    public suspend fun updateStatus(
        trackId: String,
        status: TrackStatus,
        updatedAt: Long,
    )

    /** Ends a recording: the final [status] and the wall-clock time it ended. */
    @Query(
        """
        UPDATE track
        SET status = :status, ended_at = :endedAt, updated_at = :updatedAt
        WHERE id = :trackId
        """,
    )
    public suspend fun endTrack(
        trackId: String,
        status: TrackStatus,
        endedAt: Long,
        updatedAt: Long,
    )

    @Query("UPDATE track SET sync_state = :syncState WHERE id = :trackId")
    public suspend fun updateSyncState(
        trackId: String,
        syncState: SyncState,
    )

    @Query(
        """
        UPDATE track
        SET simplified_geojson = :geoJson, updated_at = :updatedAt
        WHERE id = :trackId
        """,
    )
    public suspend fun updateSimplifiedGeoJson(
        trackId: String,
        geoJson: String?,
        updatedAt: Long,
    )

    /**
     * The user's delete: the row and every point stay, marked discarded and queued to sync
     * again, so the server can learn of it. Returns the number of rows changed, 0 for no such
     * track.
     */
    @Query(
        """
        UPDATE track
        SET status = 'discarded', sync_state = 'pending', updated_at = :updatedAt
        WHERE id = :trackId
        """,
    )
    public suspend fun discardTrack(
        trackId: String,
        updatedAt: Long,
    ): Int

    /**
     * Undoes a stop: a completed track goes back to active, with no end time. Only a
     * `completed` row is touched, so a discarded track is never revived. Returns the number of
     * rows changed, 0 when there is no such completed track.
     */
    @Query(
        """
        UPDATE track
        SET status = 'active', ended_at = NULL, updated_at = :updatedAt
        WHERE id = :trackId AND status = 'completed'
        """,
    )
    public suspend fun reopenTrack(
        trackId: String,
        updatedAt: Long,
    ): Int

    /** Cascades to `track_point`. */
    @Query("DELETE FROM track WHERE id = :trackId")
    public suspend fun deleteTrack(trackId: String)
}
