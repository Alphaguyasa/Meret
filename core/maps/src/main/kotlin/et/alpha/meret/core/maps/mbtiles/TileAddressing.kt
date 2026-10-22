package et.alpha.meret.core.maps.mbtiles

/** The deepest zoom any slippy-map scheme uses. Above this, `1 shl z` overflows an Int. */
internal const val MAX_ZOOM: Int = 30

/**
 * Whether `z/x/y` names a tile that can exist in the XYZ scheme MapLibre requests.
 *
 * A request for anything else is a malformed URL, not a missing tile, and never reaches
 * SQLite.
 */
public fun isValidTile(
    z: Int,
    x: Int,
    y: Int,
): Boolean {
    if (z !in 0..MAX_ZOOM) return false
    val tilesPerSide = 1 shl z
    return x in 0 until tilesPerSide && y in 0 until tilesPerSide
}

/**
 * Converts an XYZ row (origin top-left, what MapLibre requests) to the TMS row MBTiles
 * stores (origin bottom-left).
 *
 * Skipping this does not fail loudly: every zoom level renders, mirrored top to bottom, and
 * looks like corrupt data rather than a bug. The conversion is its own inverse.
 */
public fun xyzToTmsRow(
    z: Int,
    y: Int,
): Int {
    require(z in 0..MAX_ZOOM) { "zoom $z is out of range" }
    return (1 shl z) - 1 - y
}

/**
 * Whether [bytes] start with the gzip magic number (`1f 8b`).
 *
 * Planetiler gzips every tile. They are served as stored with `Content-Encoding: gzip`, so
 * the phone never decompresses a tile only for MapLibre to compress-check it again.
 */
public fun isGzip(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == GZIP_MAGIC_0 && bytes[1] == GZIP_MAGIC_1

private const val GZIP_MAGIC_0: Byte = 0x1f
private const val GZIP_MAGIC_1: Byte = 0x8b.toByte()
