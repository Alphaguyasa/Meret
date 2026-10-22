package et.alpha.meret.core.maps.mbtiles

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/**
 * Reads tiles and metadata from an `.mbtiles` pack, opened read-only.
 *
 * Every call blocks on SQLite. The loopback server calls it from its own I/O threads; nothing
 * on the main thread may. One reader is safe to share between threads, because
 * [SQLiteDatabase] serialises access to its connection.
 *
 * Works with both plain packs (a `tiles` table) and Planetiler's deduplicated ones (a `tiles`
 * view over `tiles_shallow` and `tiles_data`), since both expose the same `tiles` columns.
 *
 * A tile larger than the 2 MB cursor window cannot be read. Planetiler warns well below that
 * (500 kB), so a pack that hits it was built wrong.
 */
public class MbtilesReader private constructor(
    private val database: SQLiteDatabase,
) : TileSource {
    /** Reads and validates the `metadata` table. */
    override fun readMetadata(): MbtilesMetadata {
        val rows =
            guard("metadata table is unreadable") {
                database.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                    buildMap {
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(0)
                            val value = cursor.getString(1)
                            if (name != null && value != null) put(name.trim().lowercase(), value)
                        }
                    }
                }
            }
        return MbtilesMetadata.fromRows(rows)
    }

    /**
     * The tile at XYZ `z/x/y`, exactly as stored (gzipped or not), or null when the pack has
     * no tile there or the address is out of range.
     */
    override fun readTile(
        z: Int,
        x: Int,
        y: Int,
    ): Tile? {
        if (!isValidTile(z, x, y)) return null
        val args = arrayOf(z.toString(), x.toString(), xyzToTmsRow(z, y).toString())
        return guard("tiles table is unreadable") {
            database.rawQuery(TILE_QUERY, args).use { cursor ->
                if (cursor.moveToFirst()) cursor.getBlob(0)?.let(::Tile) else null
            }
        }
    }

    override fun close() {
        database.close()
    }

    public companion object {
        /**
         * Opens [file] read-only. Throws [InvalidMbtilesException] when it is missing or not
         * an SQLite database; the tables are checked on first read.
         */
        public fun open(file: File): MbtilesReader {
            if (!file.isFile) throw InvalidMbtilesException("pack file does not exist")
            val database =
                guard("pack is not an SQLite database") {
                    SQLiteDatabase.openDatabase(file.path, null, OPEN_FLAGS).apply(::probe)
                }
            return MbtilesReader(database)
        }

        private const val OPEN_FLAGS =
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS

        private const val PROBE_QUERY = "SELECT count(*) FROM sqlite_master"

        // The bound arguments are strings; CAST keeps the comparison numeric whatever the
        // column affinity of a view turns out to be.
        private const val TILE_QUERY =
            "SELECT tile_data FROM tiles " +
                "WHERE zoom_level = CAST(? AS INTEGER) " +
                "AND tile_column = CAST(? AS INTEGER) " +
                "AND tile_row = CAST(? AS INTEGER) " +
                "LIMIT 1"

        /** openDatabase reads no pages, so a file that is not SQLite only fails on a query. */
        private fun probe(database: SQLiteDatabase) {
            try {
                database.rawQuery(PROBE_QUERY, null).use { it.moveToFirst() }
            } catch (e: SQLiteException) {
                database.close()
                throw e
            }
        }

        private inline fun <T> guard(
            reason: String,
            block: () -> T,
        ): T = try {
            block()
        } catch (e: SQLiteException) {
            throw InvalidMbtilesException(reason, e)
        }
    }
}
