package et.alpha.meret.core.maps.mbtiles

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.GZIPInputStream

/** Against real SQLite under Robolectric, on fixture packs written by [MbtilesFixture]. */
@RunWith(RobolectricTestRunner::class)
class MbtilesReaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val readers = mutableListOf<MbtilesReader>()

    @After
    fun closeReaders() {
        readers.forEach { it.close() }
    }

    @Test
    fun `metadata comes back as Planetiler wrote it`() {
        val reader = open(MbtilesFixture.ethiopia(pack()).write())

        assertThat(reader.readMetadata()).isEqualTo(
            MbtilesMetadata(
                name = "OpenMapTiles",
                format = "pbf",
                minZoom = 0,
                maxZoom = 14,
                bounds = TileBounds(west = 32.99, south = 3.40, east = 47.99, north = 14.89),
                center = TileCenter(longitude = 38.7578, latitude = 9.0300, zoom = 6),
                attribution = "© OpenStreetMap contributors",
                json = """{"vector_layers":[{"id":"water","fields":{}}]}""",
            ),
        )
    }

    @Test
    fun `an XYZ request reads the mirrored TMS row`() {
        // One column at z2, a different blob in each of its four TMS rows.
        val fixture = MbtilesFixture.ethiopia(pack())
        for (tmsRow in 0..3) {
            fixture.tile(z = 2, column = 1, tmsRow = tmsRow, blob = "tms-$tmsRow".toByteArray())
        }
        val reader = open(fixture.write())

        // XYZ row 0 is the top of the map; TMS row 0 is the bottom.
        val byXyzRow = (0..3).map { y -> String(requireNotNull(reader.readTile(2, 1, y)).bytes) }

        assertThat(byXyzRow).containsExactly("tms-3", "tms-2", "tms-1", "tms-0").inOrder()
    }

    @Test
    fun `a gzipped tile is served byte for byte, flagged as gzip`() {
        val stored = MbtilesFixture.gzip("vector tile for Addis Ababa")
        val reader = open(MbtilesFixture.ethiopia(pack()).tile(0, 0, 0, stored).write())

        val tile = requireNotNull(reader.readTile(0, 0, 0))

        assertThat(tile.bytes).isEqualTo(stored)
        assertThat(tile.isGzipped).isTrue()
        assertThat(String(GZIPInputStream(tile.bytes.inputStream()).readBytes()))
            .isEqualTo("vector tile for Addis Ababa")
    }

    @Test
    fun `an uncompressed tile is served as stored, not flagged`() {
        val stored = byteArrayOf(0x1a, 0x05, 0x0a, 0x03)
        val reader = open(MbtilesFixture.ethiopia(pack()).tile(0, 0, 0, stored).write())

        val tile = requireNotNull(reader.readTile(0, 0, 0))

        assertThat(tile.bytes).isEqualTo(stored)
        assertThat(tile.isGzipped).isFalse()
    }

    @Test
    fun `a tile the pack does not hold is null`() {
        val reader =
            open(MbtilesFixture.ethiopia(pack()).tile(0, 0, 0, "world".toByteArray()).write())

        assertThat(reader.readTile(1, 0, 0)).isNull()
        assertThat(reader.readTile(14, 9_955, 7_779)).isNull()
    }

    @Test
    fun `an address outside the tile grid is null`() {
        val reader =
            open(MbtilesFixture.ethiopia(pack()).tile(0, 0, 0, "world".toByteArray()).write())

        assertThat(reader.readTile(0, 1, 0)).isNull()
        assertThat(reader.readTile(0, 0, -1)).isNull()
        assertThat(reader.readTile(-1, 0, 0)).isNull()
        assertThat(reader.readTile(31, 0, 0)).isNull()
    }

    @Test
    fun `a deduplicated pack reads through its tiles view`() {
        val shared = MbtilesFixture.gzip("ocean")
        val fixture =
            MbtilesFixture
                .ethiopia(pack(), deduplicated = true)
                .tile(z = 1, column = 0, tmsRow = 0, blob = shared)
                .tile(z = 1, column = 1, tmsRow = 1, blob = "land".toByteArray())
        val reader = open(fixture.write())

        assertThat(requireNotNull(reader.readTile(1, 0, 1)).bytes).isEqualTo(shared)
        assertThat(String(requireNotNull(reader.readTile(1, 1, 0)).bytes)).isEqualTo("land")
        assertThat(reader.readTile(1, 0, 0)).isNull()
        assertThat(reader.readMetadata().format).isEqualTo("pbf")
    }

    @Test
    fun `a file that is not SQLite is rejected on open`() {
        val notAPack = folder.newFile("ethiopia.mbtiles").apply { writeText("PK this is a zip") }

        val error =
            assertThrows(InvalidMbtilesException::class.java) { MbtilesReader.open(notAPack) }

        assertThat(error).hasMessageThat().doesNotContain(notAPack.path)
    }

    @Test
    fun `a missing file is rejected on open`() {
        assertThrows(InvalidMbtilesException::class.java) {
            MbtilesReader.open(File(folder.root, "missing.mbtiles"))
        }
    }

    @Test
    fun `an SQLite file without MBTiles tables is rejected on first read`() {
        val file = pack()
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE notes (body TEXT)")
        }
        val reader = open(file)

        assertThrows(InvalidMbtilesException::class.java) { reader.readMetadata() }
        assertThrows(InvalidMbtilesException::class.java) { reader.readTile(0, 0, 0) }
    }

    @Test
    fun `the reader never writes to the pack`() {
        val file = MbtilesFixture.ethiopia(pack()).tile(0, 0, 0, "world".toByteArray()).write()
        val before = file.readBytes()

        open(file).apply {
            readMetadata()
            readTile(0, 0, 0)
            close()
        }

        assertThat(file.readBytes()).isEqualTo(before)
    }

    private fun pack(): File =
        File(folder.root, "pack-${readers.size}-${System.nanoTime()}.mbtiles")

    private fun open(file: File): MbtilesReader = MbtilesReader.open(file).also { readers += it }
}
