package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.TilePackSource

/**
 * An installed offline basemap: one `.mbtiles` file plus enough metadata to decide whether
 * it covers where the user is standing, without opening it.
 *
 * [path] is a local filesystem path, never a URL. The bbox columns are non-null here —
 * unlike a track's, which accumulate — because a pack whose extent is unknown cannot be
 * matched to a location and has no reason to be in the table.
 */
@Entity(tableName = "tile_pack")
public data class TilePackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "name")
    public val name: String,
    @ColumnInfo(name = "region_code")
    public val regionCode: String,
    @ColumnInfo(name = "path")
    public val path: String,
    @ColumnInfo(name = "bytes")
    public val bytes: Long,
    @ColumnInfo(name = "min_zoom")
    public val minZoom: Int,
    @ColumnInfo(name = "max_zoom")
    public val maxZoom: Int,
    @ColumnInfo(name = "bbox_min_lat")
    public val bboxMinLatitude: Double,
    @ColumnInfo(name = "bbox_min_lon")
    public val bboxMinLongitude: Double,
    @ColumnInfo(name = "bbox_max_lat")
    public val bboxMaxLatitude: Double,
    @ColumnInfo(name = "bbox_max_lon")
    public val bboxMaxLongitude: Double,
    @ColumnInfo(name = "source")
    public val source: TilePackSource,
    @ColumnInfo(name = "installed_at")
    public val installedAt: Long,
    @ColumnInfo(name = "style_id")
    public val styleId: String? = null,
)
