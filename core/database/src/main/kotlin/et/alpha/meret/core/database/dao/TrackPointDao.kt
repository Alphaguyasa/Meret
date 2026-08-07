package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import et.alpha.meret.core.database.entity.TrackGeometryPoint
import et.alpha.meret.core.database.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
public interface TrackPointDao {
    @Query(
        """
        SELECT * FROM track_point
        WHERE track_id = :trackId
        ORDER BY segment_index ASC, seq ASC
        """,
    )
    public fun observePoints(trackId: String): Flow<List<TrackPointEntity>>

    @Query(
        """
        SELECT * FROM track_point
        WHERE track_id = :trackId
        ORDER BY segment_index ASC, seq ASC
        """,
    )
    public suspend fun getPoints(trackId: String): List<TrackPointEntity>

    /**
     * One sync chunk. The cursor is `seq`, not an offset — an OFFSET walks and discards
     * every earlier row, which on a 100,000-point track makes the last chunk quadratically
     * expensive to reach.
     */
    @Query(
        """
        SELECT * FROM track_point
        WHERE track_id = :trackId AND seq > :afterSeq
        ORDER BY seq ASC
        LIMIT :limit
        """,
    )
    public suspend fun getPointsAfter(
        trackId: String,
        afterSeq: Int,
        limit: Int,
    ): List<TrackPointEntity>

    /**
     * The line to draw, and nothing else. See [TrackGeometryPoint] for why this exists
     * alongside [observePoints].
     */
    @Query(
        """
        SELECT segment_index,
               seq,
               COALESCE(filtered_lat, lat) AS lat,
               COALESCE(filtered_lon, lon) AS lon
        FROM track_point
        WHERE track_id = :trackId AND is_outlier = 0
        ORDER BY segment_index ASC, seq ASC
        """,
    )
    public fun observeTrackGeometry(trackId: String): Flow<List<TrackGeometryPoint>>

    @Query(
        """
        SELECT segment_index,
               seq,
               COALESCE(filtered_lat, lat) AS lat,
               COALESCE(filtered_lon, lon) AS lon
        FROM track_point
        WHERE track_id = :trackId AND is_outlier = 0
        ORDER BY segment_index ASC, seq ASC
        """,
    )
    public suspend fun getTrackGeometry(trackId: String): List<TrackGeometryPoint>

    /** Null when the track has no points yet. The next fix takes `(this ?: -1) + 1`. */
    @Query("SELECT MAX(seq) FROM track_point WHERE track_id = :trackId")
    public suspend fun getMaxSeq(trackId: String): Int?

    @Query("SELECT COUNT(*) FROM track_point WHERE track_id = :trackId")
    public suspend fun countPoints(trackId: String): Int

    @Query("SELECT MAX(segment_index) FROM track_point WHERE track_id = :trackId")
    public suspend fun getMaxSegmentIndex(trackId: String): Int?

    /**
     * Ignores rows that collide on `(track_id, seq)` or on the primary key.
     *
     * IGNORE rather than REPLACE is load-bearing: a replayed chunk must be a no-op, and
     * REPLACE would delete and re-insert the row, which cascades and rewrites data that
     * CLAUDE.md invariant 2 says is immutable.
     *
     * **Returns nothing on purpose.** Declaring `List<Long>` here makes Room call
     * `getLastInsertedRowId` after every row, and that helper runs `SELECT changes()` and
     * `SELECT last_insert_rowid()` as two freshly prepared statements — per point. Measured
     * on a Galaxy S9+: 800 µs/row with the row ids, 54 µs/row without, for the same insert.
     * [appendBatch] counts what landed with a [totalChanges] delta instead: two statements
     * per batch rather than two per fix.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertPoints(points: List<TrackPointEntity>)

    /**
     * SQLite's cumulative row-change counter for this connection. O(1), and stable inside a
     * transaction because a transaction is pinned to one connection — which is what makes a
     * before/after delta an exact count of the rows an insert actually accepted.
     */
    @Query("SELECT total_changes()")
    public suspend fun totalChanges(): Long

    @Query("DELETE FROM track_point WHERE track_id = :trackId")
    public suspend fun deletePoints(trackId: String)

    /**
     * Writes a batch of fixes and the track counters they imply, atomically.
     *
     * This is the only path the location engine uses. Everything a fix changes — the rows,
     * the point count, the running distance, the moving duration and the bounding box —
     * lands in one transaction, so a process kill between the insert and the counter update
     * cannot leave a track claiming a distance its points do not support.
     *
     * Returns the number of rows actually inserted. A replayed batch returns 0 and moves
     * no counter, which is what makes the write idempotent.
     */
    @Transaction
    public suspend fun appendBatch(
        trackId: String,
        points: List<TrackPointEntity>,
        distanceDeltaMeters: Double,
        movingDurationDeltaMillis: Long,
        updatedAt: Long,
    ): Int {
        if (points.isEmpty()) return 0

        val changesBefore = totalChanges()
        insertPoints(points)
        val insertedCount = (totalChanges() - changesBefore).toInt()

        if (insertedCount > 0) {
            updateTotals(
                trackId = trackId,
                addedPoints = insertedCount,
                addedDistanceMeters = distanceDeltaMeters,
                addedMovingDurationMillis = movingDurationDeltaMillis,
                updatedAt = updatedAt,
            )
            updateBoundsFor(trackId, points, wholeBatchLanded = insertedCount == points.size)
        }

        return insertedCount
    }

    /**
     * Widens the track's bounds to cover a batch that has just landed.
     *
     * The fast path — nothing in the batch collided — takes min/max straight from the fixes
     * in hand. That is exact, because MIN/MAX is idempotent: re-applying a coordinate the
     * box already covers changes nothing.
     *
     * A partial collision is the awkward case. We know how many rows landed but not which,
     * and the ones that did *not* land were rejected precisely because a row with that
     * `seq` already exists — with different coordinates. Folding a rejected fix into the box
     * would stretch the map to a point that is not in the table. So that path recomputes
     * from what is actually stored. It costs a scan of one track and only happens on a
     * partially-replayed chunk, which is a sync-path event, not a fix-path one.
     */
    private suspend fun updateBoundsFor(
        trackId: String,
        points: List<TrackPointEntity>,
        wholeBatchLanded: Boolean,
    ) {
        if (!wholeBatchLanded) {
            recomputeBounds(trackId)
            return
        }

        // Rejected fixes are stored but must not stretch the box the map fits to. A single
        // 40 km outlier would otherwise zoom the whole track out to nothing.
        val bounded = points.filterNot { it.isOutlier }
        if (bounded.isEmpty()) return

        val latitudes = bounded.map { it.filteredLatitude ?: it.latitude }
        val longitudes = bounded.map { it.filteredLongitude ?: it.longitude }
        expandBounds(
            trackId = trackId,
            minLatitude = latitudes.min(),
            minLongitude = longitudes.min(),
            maxLatitude = latitudes.max(),
            maxLongitude = longitudes.max(),
        )
    }

    /** Rebuilds a track's bounds from the fixes actually stored for it. */
    @Query(
        """
        UPDATE track SET
            bbox_min_lat = (
                SELECT MIN(COALESCE(filtered_lat, lat)) FROM track_point
                WHERE track_id = :trackId AND is_outlier = 0
            ),
            bbox_min_lon = (
                SELECT MIN(COALESCE(filtered_lon, lon)) FROM track_point
                WHERE track_id = :trackId AND is_outlier = 0
            ),
            bbox_max_lat = (
                SELECT MAX(COALESCE(filtered_lat, lat)) FROM track_point
                WHERE track_id = :trackId AND is_outlier = 0
            ),
            bbox_max_lon = (
                SELECT MAX(COALESCE(filtered_lon, lon)) FROM track_point
                WHERE track_id = :trackId AND is_outlier = 0
            )
        WHERE id = :trackId
        """,
    )
    public suspend fun recomputeBounds(trackId: String)

    @Query(
        """
        UPDATE track SET
            point_count = point_count + :addedPoints,
            distance_m = distance_m + :addedDistanceMeters,
            duration_moving_ms = duration_moving_ms + :addedMovingDurationMillis,
            updated_at = :updatedAt
        WHERE id = :trackId
        """,
    )
    public suspend fun updateTotals(
        trackId: String,
        addedPoints: Int,
        addedDistanceMeters: Double,
        addedMovingDurationMillis: Long,
        updatedAt: Long,
    )

    /**
     * Widens the track's bounding box to include a batch. COALESCE covers the first fix,
     * where all four columns are still null.
     */
    @Query(
        """
        UPDATE track SET
            bbox_min_lat = MIN(COALESCE(bbox_min_lat, :minLatitude), :minLatitude),
            bbox_min_lon = MIN(COALESCE(bbox_min_lon, :minLongitude), :minLongitude),
            bbox_max_lat = MAX(COALESCE(bbox_max_lat, :maxLatitude), :maxLatitude),
            bbox_max_lon = MAX(COALESCE(bbox_max_lon, :maxLongitude), :maxLongitude)
        WHERE id = :trackId
        """,
    )
    public suspend fun expandBounds(
        trackId: String,
        minLatitude: Double,
        minLongitude: Double,
        maxLatitude: Double,
        maxLongitude: Double,
    )
}
