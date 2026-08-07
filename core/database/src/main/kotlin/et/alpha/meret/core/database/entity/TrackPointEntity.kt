package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.LocationProvider

/**
 * A single fix. Raw, immutable, and the only table here that will reach millions of rows.
 *
 * Keep it narrow. There is no `sync_state` column and there must never be one: points sync
 * as part of a track chunk, and a per-row state would add an index over millions of rows to
 * answer a question the parent track already answers.
 *
 * [latitude]/[longitude] are exactly what the platform reported and are never rewritten.
 * [filteredLatitude]/[filteredLongitude] hold the Kalman output beside them so filtering can
 * be re-run later against the original data, per CLAUDE.md invariant 2.
 *
 * The unique index on `(track_id, seq)` is what makes a replayed insert a no-op: a chunk
 * re-sent after a crash collides and is ignored rather than duplicating the line.
 */
@Entity(
    tableName = "track_point",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["track_id", "seq"],
            unique = true,
            name = "index_track_point_track_seq",
        ),
    ],
)
public data class TrackPointEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "track_id")
    public val trackId: String,
    /** Increments on each pause/resume, so a gap is a segment break and not a straight line. */
    @ColumnInfo(name = "segment_index")
    public val segmentIndex: Int = 0,
    /** Monotonic within the track, assigned at write. Unique with [trackId]. */
    @ColumnInfo(name = "seq")
    public val seq: Int,
    @ColumnInfo(name = "lat")
    public val latitude: Double,
    @ColumnInfo(name = "lon")
    public val longitude: Double,
    @ColumnInfo(name = "altitude_m")
    public val altitudeMeters: Double? = null,
    @ColumnInfo(name = "accuracy_m")
    public val accuracyMeters: Double,
    @ColumnInfo(name = "vertical_accuracy_m")
    public val verticalAccuracyMeters: Double? = null,
    @ColumnInfo(name = "bearing_deg")
    public val bearingDegrees: Double? = null,
    @ColumnInfo(name = "speed_mps")
    public val speedMetersPerSecond: Double? = null,
    @ColumnInfo(name = "speed_accuracy_mps")
    public val speedAccuracyMetersPerSecond: Double? = null,
    @ColumnInfo(name = "provider")
    public val provider: LocationProvider,
    @ColumnInfo(name = "satellites_used")
    public val satellitesUsed: Int? = null,
    /** Wall clock. Field device clocks are routinely wrong; do not order by this. */
    @ColumnInfo(name = "recorded_at")
    public val recordedAt: Long,
    /** Monotonic since boot. This is the intra-track ordering key. */
    @ColumnInfo(name = "elapsed_realtime_nanos")
    public val elapsedRealtimeNanos: Long,
    @ColumnInfo(name = "is_mock")
    public val isMock: Boolean = false,
    @ColumnInfo(name = "filtered_lat")
    public val filteredLatitude: Double? = null,
    @ColumnInfo(name = "filtered_lon")
    public val filteredLongitude: Double? = null,
    @ColumnInfo(name = "filter_variance")
    public val filterVariance: Double? = null,
    /** Rejected by the validity gate. Kept for audit — it is evidence, not noise. */
    @ColumnInfo(name = "is_outlier")
    public val isOutlier: Boolean = false,
)
