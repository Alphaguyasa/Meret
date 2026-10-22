package et.alpha.meret.core.maps.mbtiles

import java.io.Closeable

/**
 * One tile pack, as the loopback server reads it. [MbtilesReader] is the production
 * implementation; tests use an in-memory one.
 *
 * Calls may block and may arrive from several server threads at once.
 */
public interface TileSource : Closeable {
    /** Throws [InvalidMbtilesException] when the pack cannot be served. */
    public fun readMetadata(): MbtilesMetadata

    /** The tile at XYZ `z/x/y` exactly as stored, or null when the pack has none there. */
    public fun readTile(
        z: Int,
        x: Int,
        y: Int,
    ): Tile?
}
