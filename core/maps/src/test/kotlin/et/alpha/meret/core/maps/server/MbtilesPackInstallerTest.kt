package et.alpha.meret.core.maps.server

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesFixture
import et.alpha.meret.core.maps.mbtiles.TileBounds
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Real SQLite packs, written by [MbtilesFixture], copied the way a picked file would be. */
@RunWith(RobolectricTestRunner::class)
class MbtilesPackInstallerTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var packs: File
    private lateinit var installer: MbtilesPackInstaller

    @Before
    fun setUp() {
        packs = File(temp.root, "packs")
        installer = MbtilesPackInstaller(packs)
    }

    @Test
    fun `a vector pack is installed where the map reads it`() {
        val picked = MbtilesFixture.ethiopia(temp.newFile("picked.mbtiles")).withTile().write()

        val installed = installer.install(PACK_ID, picked.inputStream(), picked.length())

        assertThat(installed.file).isEqualTo(File(packs, "$PACK_ID.mbtiles"))
        assertThat(installed.bytes).isEqualTo(picked.length())
        assertThat(installed.file.readBytes()).isEqualTo(picked.readBytes())
        assertThat(installed.metadata.minZoom).isEqualTo(0)
        assertThat(installed.metadata.maxZoom).isEqualTo(14)
        assertThat(installed.bounds).isEqualTo(TileBounds(32.99, 3.40, 47.99, 14.89))
        PackDirectoryResolver(packs).open(PACK_ID)!!.use {
            assertThat(it.readMetadata().format).isEqualTo("pbf")
        }
    }

    @Test
    fun `a pack with no bounds covers the whole world`() {
        val picked =
            MbtilesFixture(temp.newFile("nobounds.mbtiles"))
                .metadata("format", "pbf")
                .metadata("minzoom", "0")
                .metadata("maxzoom", "10")
                .withTile()
                .write()

        val installed = installer.install(PACK_ID, picked.inputStream(), null)

        assertThat(installed.bounds.west).isEqualTo(-180.0)
        assertThat(installed.bounds.east).isEqualTo(180.0)
        assertThat(installed.bounds.north).isGreaterThan(85.0)
    }

    @Test
    fun `a raster pack is refused and leaves nothing behind`() {
        val picked =
            MbtilesFixture
                .ethiopia(temp.newFile("raster.mbtiles"))
                .metadata("format", "png")
                .withTile()
                .write()

        assertThrows(InvalidMbtilesException::class.java) {
            installer.install(PACK_ID, picked.inputStream(), null)
        }
        assertThat(packs.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a file that is not a database is refused and leaves nothing behind`() {
        val picked = "not a database, just text".byteInputStream()

        assertThrows(InvalidMbtilesException::class.java) {
            installer.install(PACK_ID, picked, null)
        }
        assertThat(packs.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a pack with no tiles table is refused`() {
        val picked = File(temp.root, "notiles.mbtiles")
        SQLiteDatabase.openOrCreateDatabase(picked, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            db.execSQL("INSERT INTO metadata VALUES ('format', 'pbf')")
            db.execSQL("INSERT INTO metadata VALUES ('minzoom', '0')")
            db.execSQL("INSERT INTO metadata VALUES ('maxzoom', '14')")
        }

        assertThrows(InvalidMbtilesException::class.java) {
            installer.install(PACK_ID, picked.inputStream(), null)
        }
        assertThat(packs.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a copy that fails part way leaves nothing behind`() {
        val failing =
            object : InputStream() {
                private var sent = 0

                override fun read(): Int {
                    if (sent++ > FAIL_AFTER_BYTES) throw IOException("USB cable pulled")
                    return 0
                }
            }

        assertThrows(IOException::class.java) { installer.install(PACK_ID, failing, null) }
        assertThat(packs.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a pack larger than the free space is refused before copying`() {
        var read = false
        val source =
            object : InputStream() {
                override fun read(): Int {
                    read = true
                    return -1
                }
            }

        assertThrows(InsufficientSpaceException::class.java) {
            installer.install(PACK_ID, source, Long.MAX_VALUE)
        }
        assertThat(read).isFalse()
        assertThat(File(packs, "$PACK_ID.mbtiles").exists()).isFalse()
    }

    @Test
    fun `a copy a crash cut short is cleaned up by the next install`() {
        packs.mkdirs()
        val leftover = File(packs, "older.mbtiles.partial").apply { writeText("half a pack") }
        val picked = MbtilesFixture.ethiopia(temp.newFile("picked.mbtiles")).withTile().write()

        installer.install(PACK_ID, picked.inputStream(), null)

        assertThat(leftover.exists()).isFalse()
        assertThat(packs.list()).asList().containsExactly("$PACK_ID.mbtiles")
    }

    @Test
    fun `the source is closed whatever happens`() {
        var closed = false
        val source =
            object : ByteArrayInputStream("garbage".toByteArray()) {
                override fun close() {
                    closed = true
                }
            }

        assertThrows(InvalidMbtilesException::class.java) {
            installer.install(PACK_ID, source, null)
        }
        assertThat(closed).isTrue()
    }

    @Test
    fun `uninstall deletes the pack file`() {
        val picked = MbtilesFixture.ethiopia(temp.newFile("picked.mbtiles")).withTile().write()
        installer.install(PACK_ID, picked.inputStream(), null)

        installer.uninstall(PACK_ID)
        installer.uninstall(PACK_ID)

        assertThat(File(packs, "$PACK_ID.mbtiles").exists()).isFalse()
    }

    @Test
    fun `packs with no row are deleted, registered packs and other files are kept`() {
        packs.mkdirs()
        val registered = File(packs, "kept.mbtiles").apply { writeText("pack") }
        val orphan = File(packs, "orphan.mbtiles").apply { writeText("pack") }
        val leftover = File(packs, "cut.mbtiles.partial").apply { writeText("half") }
        val notAPack = File(packs, "notes.txt").apply { writeText("text") }

        val deleted = installer.deleteUnregistered(setOf("kept"))

        assertThat(deleted).isEqualTo(1)
        assertThat(registered.exists()).isTrue()
        assertThat(notAPack.exists()).isTrue()
        assertThat(orphan.exists()).isFalse()
        assertThat(leftover.exists()).isFalse()
    }

    @Test
    fun `a sweep of a missing directory deletes nothing`() {
        assertThat(installer.deleteUnregistered(emptySet())).isEqualTo(0)
    }

    private fun MbtilesFixture.withTile(): MbtilesFixture =
        tile(z = 0, column = 0, tmsRow = 0, blob = MbtilesFixture.gzip("tile"))

    private companion object {
        const val PACK_ID = "0192f3a0-7b1c-7d2e-8f00-123456789abc"
        const val FAIL_AFTER_BYTES = 10_000
    }
}
