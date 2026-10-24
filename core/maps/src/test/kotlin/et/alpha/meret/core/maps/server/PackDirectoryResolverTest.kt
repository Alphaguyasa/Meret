package et.alpha.meret.core.maps.server

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import et.alpha.meret.core.maps.mbtiles.Tile
import et.alpha.meret.core.maps.mbtiles.TileSource
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PackDirectoryResolverTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var packs: File
    private val opened = mutableListOf<File>()
    private val pack = EmptyPack()

    @Before
    fun createDirectory() {
        packs = temp.newFolder("packs")
    }

    @Test
    fun `an id opens the mbtiles file of that name in the directory`() {
        val file = File(packs, "ethiopia.mbtiles").apply { writeText("pack") }

        val source = resolver().open("ethiopia")

        assertThat(source).isSameInstanceAs(pack)
        assertThat(opened).containsExactly(file)
    }

    @Test
    fun `a pack that is not there resolves to nothing`() {
        assertThat(resolver().open("ethiopia")).isNull()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `an id that is not a pack id never touches the file system`() {
        temp.newFile("secret.mbtiles")
        File(packs, "sub").mkdir()
        File(packs, "sub/x.mbtiles").writeText("pack")

        listOf("../secret", "sub/x", "", "ethiopia.mbtiles", "a".repeat(65)).forEach {
            assertThat(resolver().open(it)).isNull()
        }
        assertThat(opened).isEmpty()
    }

    @Test
    fun `a link that leads out of the directory is not followed`() {
        val outside = temp.newFile("outside.mbtiles")
        Files.createSymbolicLink(File(packs, "linked.mbtiles").toPath(), outside.toPath())

        assertThat(resolver().open("linked")).isNull()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `a corrupt pack resolves to nothing instead of throwing`() {
        File(packs, "broken.mbtiles").writeText("not sqlite")
        val resolver = PackDirectoryResolver(packs) { throw InvalidMbtilesException("not SQLite") }

        assertThat(resolver.open("broken")).isNull()
    }

    private fun resolver() = PackDirectoryResolver(packs) { file ->
        opened += file
        pack
    }

    private class EmptyPack : TileSource {
        override fun readMetadata(): MbtilesMetadata = MbtilesMetadata.fromRows(
            mapOf("format" to "pbf", "minzoom" to "0", "maxzoom" to "14"),
        )

        override fun readTile(
            z: Int,
            x: Int,
            y: Int,
        ): Tile? = null

        override fun close() = Unit
    }
}
