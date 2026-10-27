package et.alpha.meret.core.maps

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import et.alpha.meret.core.maps.mbtiles.Tile
import et.alpha.meret.core.maps.mbtiles.TileSource
import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.style.StyleProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/** A real loopback server and the real style template; only the pack is in memory. */
class OfflineMapSessionTest {
    @get:Rule val temp = TemporaryFolder()

    private val template =
        File(requireNotNull(System.getProperty("meret.styleDir")), "meret-style.json")
    private val server = LocalTileServer { packId -> if (packId == PACK) FakePack() else null }

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `the style points at the server's TileJSON, and it answers`() = test { session ->
        val uri = session.acquire(PACK)

        val sourceUrl = sourceUrl(uri)
        assertThat(sourceUrl).isEqualTo("http://127.0.0.1:${server.port}/$PACK/tiles.json")
        val connection = URI(sourceUrl).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            assertThat(connection.responseCode).isEqualTo(200)
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `the server runs until the last map releases`() = test { session ->
        session.acquire(PACK)
        val port = server.port
        session.acquire(PACK)

        assertThat(server.port).isEqualTo(port)
        session.release()
        assertThat(server.port).isEqualTo(port)
        session.release()
        assertThat(server.port).isNull()
    }

    @Test
    fun `after a restart the style is rewritten for the new port`() = test { session ->
        session.acquire(PACK)
        session.release()

        val uri = session.acquire(PACK)

        assertThat(sourceUrl(uri)).isEqualTo("http://127.0.0.1:${server.port}/$PACK/tiles.json")
    }

    @Test
    fun `a bad pack id fails and holds nothing`() = test { session ->
        listOf("../etc", "a/b", "").forEach { packId ->
            expect(IllegalArgumentException::class.java) { session.acquire(packId) }
        }

        assertThat(server.port).isNull()
        expect(IllegalStateException::class.java) { session.release() }
    }

    @Test
    fun `a failed acquire does not stop the server under another map`() = test { session ->
        session.acquire(PACK)
        val port = server.port

        expect(IllegalArgumentException::class.java) { session.acquire("../etc") }

        assertThat(server.port).isEqualTo(port)
        session.release()
        assertThat(server.port).isNull()
    }

    private fun test(block: suspend TestScope.(OfflineMapSession) -> Unit) = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val styles = StyleProvider({ template.inputStream() }, temp.newFolder(), io)
        block(OfflineMapSession(server, styles, io))
    }

    private suspend fun expect(
        type: Class<out Throwable>,
        block: suspend () -> Unit,
    ) {
        val error = runCatching { block() }.exceptionOrNull()
        assertThat(error).isInstanceOf(type)
    }

    private fun sourceUrl(styleUri: String): String {
        assertThat(styleUri).startsWith("file://")
        val style = Json.parseToJsonElement(File(URI(styleUri)).readText()).jsonObject
        return style
            .getValue("sources")
            .jsonObject
            .getValue("openmaptiles")
            .jsonObject
            .getValue("url")
            .jsonPrimitive.content
    }

    private class FakePack : TileSource {
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

    private companion object {
        const val PACK = "ethiopia"
    }
}
