package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo

/**
 * Just enough of a fix to draw it.
 *
 * The map does not need `speed_accuracy_mps`. Materialising 20,000 full [TrackPointEntity]
 * rows costs ~535 ms on the reference device against ~103 ms for this projection, and that
 * difference is the whole budget for a screen that also has to render at 60 fps.
 *
 * The query resolves the effective position — filtered where the Kalman filter produced one,
 * raw otherwise — and drops outliers, so what arrives here is exactly the line to draw. That
 * is a display concern only: the raw fixes are all still in `track_point`, untouched.
 */
public data class TrackGeometryPoint(
    @ColumnInfo(name = "segment_index")
    public val segmentIndex: Int,
    @ColumnInfo(name = "seq")
    public val seq: Int,
    @ColumnInfo(name = "lat")
    public val latitude: Double,
    @ColumnInfo(name = "lon")
    public val longitude: Double,
)
