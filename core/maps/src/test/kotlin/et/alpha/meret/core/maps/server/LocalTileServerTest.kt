package et.alpha.meret.core.maps.server

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.mbtiles.InvalidMbtilesException
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import et.alpha.meret.core.maps.mbtiles.Tile
import et.alpha.meret.core.maps.mbtiles.TileSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

/** Real sockets on loopback, against in-memory packs. */
class LocalTileServerTest {
    private val gzippedTile = gzip("addis ababa")
    private val ethiopia =
        FakeSource(
            tiles =
                mapOf(
                    Triple(14, 9_955, 7_779) to Tile(gzippedTile),
                    Triple(0, 0, 0) to Tile("plain".toByteArray()),
                ),
        )
    private val opened = AtomicInteger()
    private val server =
        LocalTileServer { packId ->
            opened.incrementAndGet()
            if (packId == "ethiopia") ethiopia else null
        }

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `tiles json points MapLibre back at this server`() {
        val port = server.start()

        val response = get("/ethiopia/tiles.json")
        val json = Json.parseToJsonElement(String(response.body)).jsonObject

        assertThat(response.status).isEqualTo(200)
        assertThat(response.header("Content-Type")).isEqualTo("application/json; charset=utf-8")
        assertThat(
            json
                .getValue("tiles")
                .jsonArray
                .single()
                .jsonPrimitive.content,
        ).isEqualTo("http://127.0.0.1:$port/ethiopia/{z}/{x}/{y}.pbf")
        assertThat(server.baseUrl("ethiopia")).isEqualTo("http://127.0.0.1:$port/ethiopia")
    }

    @Test
    fun `a gzipped tile goes out as stored to a client that accepts gzip`() {
        server.start()

        val response = get("/ethiopia/14/9955/7779.pbf", "Accept-Encoding" to "gzip")

        assertThat(response.status).isEqualTo(200)
        assertThat(response.header("Content-Encoding")).isEqualTo("gzip")
        assertThat(response.header("Content-Type")).isEqualTo("application/x-protobuf")
        assertThat(response.body).isEqualTo(gzippedTile)
    }

    @Test
    fun `a gzipped tile is unpacked for a client that does not accept gzip`() {
        server.start()

        val response = get("/ethiopia/14/9955/7779.pbf")

        assertThat(response.header("Content-Encoding")).isNull()
        assertThat(String(response.body)).isEqualTo("addis ababa")
    }

    @Test
    fun `an uncompressed tile is never labelled gzip`() {
        server.start()

        val response = get("/ethiopia/0/0/0.pbf", "Accept-Encoding" to "gzip")

        assertThat(response.header("Content-Encoding")).isNull()
        assertThat(String(response.body)).isEqualTo("plain")
    }

    @Test
    fun `a tile the pack does not hold is 204, so MapLibre draws nothing and moves on`() {
        server.start()

        assertThat(get("/ethiopia/14/0/0.pbf").status).isEqualTo(204)
    }

    @Test
    fun `unknown packs and malformed paths are 404`() {
        server.start()

        for (path in listOf(
            "/kenya/tiles.json",
            "/ethiopia/1/a/0.pbf",
            "/ethiopia/1/0/0.png",
            "/ethiopia/1/0",
            "/..%2F..%2Fdatabases/tiles.json",
            "/",
        )) {
            assertThat(get(path).status).isEqualTo(404)
        }
    }

    @Test
    fun `only GET and HEAD are answered`() {
        server.start()

        val head = get("/ethiopia/0/0/0.pbf", method = "HEAD")
        val delete = get("/ethiopia/0/0/0.pbf", method = "DELETE")

        assertThat(head.status).isEqualTo(200)
        assertThat(head.header("Content-Length")).isEqualTo("5")
        assertThat(delete.status).isEqualTo(405)
    }

    @Test
    fun `a pack is opened once however many tiles are asked for`() {
        server.start()

        repeat(5) { get("/ethiopia/0/0/0.pbf") }
        repeat(3) { get("/kenya/0/0/0.pbf") }

        assertThat(opened.get()).isEqualTo(2)
    }

    @Test
    fun `a pack with unusable metadata is not served`() {
        val broken = FakeSource(tiles = emptyMap(), metadataError = true)
        val server = LocalTileServer { broken }
        try {
            server.start()
            assertThat(get("/broken/tiles.json", port = server.port).status).isEqualTo(404)
            assertThat(broken.closed).isTrue()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `one connection carries many requests`() {
        val port = server.start()

        rawSocket(port).use { socket ->
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            repeat(3) {
                output.write("GET /ethiopia/0/0/0.pbf HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                output.flush()
                assertThat(readRawResponse(input)).endsWith("\r\n\r\nplain")
            }
        }
    }

    @Test
    fun `a garbled request gets 400 and the connection closes`() {
        val port = server.start()

        rawSocket(port).use { socket ->
            socket.getOutputStream().write("NONSENSE\r\n\r\n".toByteArray())
            val response = String(socket.getInputStream().readBytes())

            assertThat(response).startsWith("HTTP/1.1 400 ")
            assertThat(response).contains("Connection: close")
        }
    }

    @Test
    fun `the server listens on loopback only`() {
        val port = server.start()

        val outward =
            java.net.NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        for (address in outward) {
            assertThrows(IOException::class.java) {
                Socket().use { it.connect(InetSocketAddress(address, port), 500) }
            }
        }
    }

    @Test
    fun `stop closes the port and the opened packs, and start picks a new port`() {
        val first = server.start()
        get("/ethiopia/0/0/0.pbf")

        assertThat(server.start()).isEqualTo(first)
        server.stop()
        server.stop()

        assertThat(server.port).isNull()
        assertThat(ethiopia.closed).isTrue()
        // Not a bare connect: once freed, the ephemeral port can be taken by another
        // process, or a loopback connect can land on itself. What matters is that no tile
        // server answers there any more.
        val stale = runCatching { get("/ethiopia/0/0/0.pbf", port = first).status }
        assertThat(stale.getOrNull()).isNotEqualTo(200)
        assertThat(server.start()).isNotNull()
        assertThat(get("/ethiopia/0/0/0.pbf").status).isEqualTo(200)
    }

    private class FakeSource(
        private val tiles: Map<Triple<Int, Int, Int>, Tile>,
        private val metadataError: Boolean = false,
    ) : TileSource {
        @Volatile var closed = false

        override fun readMetadata(): MbtilesMetadata {
            if (metadataError) throw InvalidMbtilesException("metadata has no format")
            return MbtilesMetadata.fromRows(
                mapOf(
                    "format" to "pbf",
                    "minzoom" to "0",
                    "maxzoom" to "14",
                ),
            )
        }

        override fun readTile(
            z: Int,
            x: Int,
            y: Int,
        ): Tile? = tiles[Triple(z, x, y)]

        override fun close() {
            closed = true
        }
    }

    private class Response(
        val status: Int,
        val body: ByteArray,
        private val headers: Map<String, List<String>>,
    ) {
        fun header(name: String): String? = headers.entries
            .firstOrNull { name.equals(it.key, ignoreCase = true) }
            ?.value
            ?.single()
    }

    private fun get(
        path: String,
        vararg headers: Pair<String, String>,
        method: String = "GET",
        port: Int? = server.port,
    ): Response {
        val connection =
            URI(
                "http://127.0.0.1:$port$path",
            ).toURL().openConnection() as HttpURLConnection
        // Every request has a deadline. Without one, a request to a freed port that another
        // loopback listener has taken (a Gradle worker, say) waits for a reply forever.
        connection.connectTimeout = CLIENT_TIMEOUT_MS
        connection.readTimeout = CLIENT_TIMEOUT_MS
        connection.requestMethod = method
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val status = connection.responseCode
        val body =
            (if (status >= 400) connection.errorStream else connection.inputStream)
                ?.use { it.readBytes() }
                ?: ByteArray(0)
        return Response(status, body, connection.headerFields)
    }

    private fun rawSocket(port: Int): Socket =
        Socket().apply {
            connect(InetSocketAddress("127.0.0.1", port), CLIENT_TIMEOUT_MS)
            soTimeout = CLIENT_TIMEOUT_MS
        }

    /** Reads one response by its Content-Length, leaving the connection open. */
    private fun readRawResponse(input: java.io.InputStream): String {
        val head = ByteArrayOutputStream()
        while (!head.toString().endsWith("\r\n\r\n")) head.write(input.read())
        val length =
            Regex("Content-Length: (\\d+)")
                .find(head.toString())!!
                .groupValues[1]
                .toInt()
        return head.toString() + String(input.readNBytes(length))
    }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
        return out.toByteArray()
    }

    private companion object {
        const val CLIENT_TIMEOUT_MS = 5_000
    }
}
