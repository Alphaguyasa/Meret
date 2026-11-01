package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo

/** The columns of a `waypoint` row the map needs. A query result, not a table. */
public data class WaypointMarkerRow(
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "lat")
    public val latitude: Double,
    @ColumnInfo(name = "lon")
    public val longitude: Double,
    @ColumnInfo(name = "category")
    public val category: String?,
)
