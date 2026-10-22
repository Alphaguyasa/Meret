package et.alpha.meret.core.maps.mbtiles

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Writes a tiny `.mbtiles` pack the way Planetiler lays one out: TMS rows, gzipped blobs,
 * and either a plain `tiles` table or the deduplicated `tiles` view.
 *
 * Built in the test rather than committed as a binary so every row it holds is readable here.
 */
internal class MbtilesFixture(
    private val file: File,
    private val deduplicated: Boolean = false,
) {
    private val metadata = linkedMapOf<String, String>()
    private val tiles = mutableListOf<Triple<Int, Int, Int>>()
    private val blobs = mutableListOf<ByteArray>()

    fun metadata(
        name: String,
        value: String,
    ): MbtilesFixture = apply { metadata[name] = value }

    /** Stores [blob] at TMS address `z/column/tmsRow`, exactly as given. */
    fun tile(
        z: Int,
        column: Int,
        tmsRow: Int,
        blob: ByteArray,
    ): MbtilesFixture = apply {
        tiles += Triple(z, column, tmsRow)
        blobs += blob
    }

    fun write(): File {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            for ((name, value) in metadata) {
                db.execSQL("INSERT INTO metadata VALUES (?, ?)", arrayOf(name, value))
            }
            if (deduplicated) writeDeduplicated(db) else writePlain(db)
        }
        return file
    }

    private fun writePlain(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, " +
                "tile_data BLOB)",
        )
        db.execSQL("CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row)")
        tiles.forEachIndexed { i, (z, column, row) ->
            db.execSQL("INSERT INTO tiles VALUES (?, ?, ?, ?)", arrayOf(z, column, row, blobs[i]))
        }
    }

    private fun writeDeduplicated(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tiles_shallow (zoom_level INTEGER, tile_column INTEGER, " +
                "tile_row INTEGER, tile_data_id INTEGER, " +
                "PRIMARY KEY (zoom_level, tile_column, tile_row)) WITHOUT ROWID",
        )
        db.execSQL("CREATE TABLE tiles_data (tile_data_id INTEGER PRIMARY KEY, tile_data BLOB)")
        db.execSQL(
            "CREATE VIEW tiles AS SELECT tiles_shallow.zoom_level AS zoom_level, " +
                "tiles_shallow.tile_column AS tile_column, tiles_shallow.tile_row AS tile_row, " +
                "tiles_data.tile_data AS tile_data FROM tiles_shallow " +
                "JOIN tiles_data ON tiles_shallow.tile_data_id = tiles_data.tile_data_id",
        )
        tiles.forEachIndexed { i, (z, column, row) ->
            db.execSQL("INSERT INTO tiles_data VALUES (?, ?)", arrayOf(i, blobs[i]))
            db.execSQL("INSERT INTO tiles_shallow VALUES (?, ?, ?, ?)", arrayOf(z, column, row, i))
        }
    }

    companion object {
        /** A pack covering Ethiopia, as Planetiler writes its metadata. */
        fun ethiopia(
            file: File,
            deduplicated: Boolean = false,
        ): MbtilesFixture = MbtilesFixture(file, deduplicated)
            .metadata("name", "OpenMapTiles")
            .metadata("format", "pbf")
            .metadata("minzoom", "0")
            .metadata("maxzoom", "14")
            .metadata("bounds", "32.99,3.40,47.99,14.89")
            .metadata("center", "38.7578,9.0300,6")
            .metadata("attribution", "© OpenStreetMap contributors")
            .metadata("json", """{"vector_layers":[{"id":"water","fields":{}}]}""")

        fun gzip(text: String): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(text.toByteArray()) }
            return out.toByteArray()
        }
    }
}
