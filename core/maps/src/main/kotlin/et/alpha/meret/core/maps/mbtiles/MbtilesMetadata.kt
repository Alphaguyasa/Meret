package et.alpha.meret.core.maps.mbtiles

/** A tile blob exactly as stored, plus whether it is gzipped. */
public class Tile(
    public val bytes: ByteArray,
) {
    public val isGzipped: Boolean = isGzip(bytes)
}

/** West, south, east, north in WGS84 degrees, as MBTiles and TileJSON order them. */
public data class TileBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

/** A default view: longitude, latitude, zoom. */
public data class TileCenter(
    val longitude: Double,
    val latitude: Double,
    val zoom: Int,
)

/**
 * The `metadata` table of an MBTiles 1.3 pack, as the loopback server needs it for TileJSON.
 *
 * [format], [minZoom] and [maxZoom] are required: without them the server cannot tell
 * MapLibre what it serves or where to stop asking. The rest are optional and stay null when
 * missing or malformed, because a pack with a typo in its `center` still has good tiles.
 */
public data class MbtilesMetadata(
    val name: String?,
    /** `pbf` for the vector packs Meret uses. The import step (phase 6g) rejects anything else. */
    val format: String,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: TileBounds?,
    val center: TileCenter?,
    val attribution: String?,
    /** The raw `json` row, which holds `vector_layers`. TileJSON passes it through. */
    val json: String?,
) {
    public companion object {
        /** Builds the metadata from the table's `name`/`value` rows. */
        public fun fromRows(rows: Map<String, String>): MbtilesMetadata {
            val format =
                rows["format"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw InvalidMbtilesException("metadata has no format")
            val minZoom = rows.zoom("minzoom")
            val maxZoom = rows.zoom("maxzoom")
            if (minZoom > maxZoom) {
                throw InvalidMbtilesException("minzoom $minZoom is above maxzoom $maxZoom")
            }
            return MbtilesMetadata(
                name = rows["name"],
                format = format,
                minZoom = minZoom,
                maxZoom = maxZoom,
                bounds = rows["bounds"]?.let(::parseBounds),
                center = rows["center"]?.let(::parseCenter),
                attribution = rows["attribution"],
                json = rows["json"],
            )
        }

        private fun Map<String, String>.zoom(key: String): Int {
            val value = get(key) ?: throw InvalidMbtilesException("metadata has no $key")
            return value.trim().toIntOrNull()?.takeIf { it in 0..MAX_ZOOM }
                ?: throw InvalidMbtilesException("$key is not a zoom level")
        }

        private fun parseBounds(value: String): TileBounds? {
            val parts = value.split(',').mapNotNull { it.trim().toDoubleOrNull() }
            return parts
                .takeIf { it.size == BOUNDS_PARTS }
                ?.let { TileBounds(west = it[0], south = it[1], east = it[2], north = it[3]) }
                ?.takeIf {
                    isLongitude(it.west) && isLongitude(it.east) &&
                        isLatitude(it.south) && isLatitude(it.north) && it.south <= it.north
                }
        }

        private fun parseCenter(value: String): TileCenter? {
            val parts = value.split(',').map { it.trim() }
            if (parts.size != CENTER_PARTS) return null
            val longitude = parts[0].toDoubleOrNull()?.takeIf(::isLongitude)
            val latitude = parts[1].toDoubleOrNull()?.takeIf(::isLatitude)
            val zoom = parts[2].toIntOrNull()?.takeIf { it in 0..MAX_ZOOM }
            return if (longitude != null && latitude != null && zoom != null) {
                TileCenter(longitude, latitude, zoom)
            } else {
                null
            }
        }

        private fun isLongitude(degrees: Double): Boolean = degrees in -MAX_LONGITUDE..MAX_LONGITUDE

        private fun isLatitude(degrees: Double): Boolean = degrees in -MAX_LATITUDE..MAX_LATITUDE

        private const val BOUNDS_PARTS = 4
        private const val CENTER_PARTS = 3
        private const val MAX_LONGITUDE = 180.0
        private const val MAX_LATITUDE = 90.0
    }
}

/** The file is not an MBTiles pack Meret can serve. The message is safe to log: it holds no path. */
public class InvalidMbtilesException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
