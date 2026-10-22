package et.alpha.meret.core.maps.server

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpTest {
    @Test
    fun `a MapLibre tile request parses`() {
        val request =
            read(
                "GET /ethiopia/14/9955/7779.pbf?v=1 HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:41234\r\n" +
                    "Accept-Encoding: gzip\r\n" +
                    "User-Agent: MapLibre Native/11\r\n\r\n",
            )!!

        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/ethiopia/14/9955/7779.pbf")
        assertThat(request.keepAlive).isTrue()
        assertThat(request.acceptsGzip).isTrue()
        assertThat(request.header("HOST")).isEqualTo("127.0.0.1:41234")
    }

    @Test
    fun `keep-alive follows the HTTP version and the Connection header`() {
        assertThat(read("GET / HTTP/1.1\r\nConnection: close\r\n\r\n")!!.keepAlive).isFalse()
        assertThat(read("GET / HTTP/1.0\r\n\r\n")!!.keepAlive).isFalse()
        assertThat(read("GET / HTTP/1.0\r\nConnection: keep-alive\r\n\r\n")!!.keepAlive).isTrue()
    }

    @Test
    fun `gzip refused with q=0 does not count as accepted`() {
        assertThat(
            read("GET / HTTP/1.1\r\nAccept-Encoding: gzip;q=0, br\r\n\r\n")!!.acceptsGzip,
        ).isFalse()
        assertThat(
            read("GET / HTTP/1.1\r\nAccept-Encoding: deflate, GZIP\r\n\r\n")!!.acceptsGzip,
        ).isTrue()
        assertThat(
            read("GET / HTTP/1.1\r\nAccept-Encoding: x-gzip-ish\r\n\r\n")!!.acceptsGzip,
        ).isFalse()
        assertThat(read("GET / HTTP/1.1\r\n\r\n")!!.acceptsGzip).isFalse()
    }

    @Test
    fun `a closed connection with nothing sent is not an error`() {
        assertThat(read("")).isNull()
    }

    @Test
    fun `malformed requests are rejected with 400`() {
        val malformed =
            listOf(
                "GET\r\n\r\n",
                "GET relative HTTP/1.1\r\n\r\n",
                "GET / SPDY/3\r\n\r\n",
                "GET / HTTP/1.1\r\nno colon here\r\n\r\n",
                "GET / HTTP/1.1\r\nHost: x\r\n", // cut off before the blank line
                "POST / HTTP/1.1\r\nContent-Length: 4\r\n\r\nbody",
                "GET / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n",
            )
        for (request in malformed) {
            val error = assertThrows(BadRequestException::class.java) { read(request) }
            assertThat(error.status).isEqualTo(Http.BAD_REQUEST)
        }
    }

    @Test
    fun `an oversized head is rejected with 431 without reading it all`() {
        val huge = "GET / HTTP/1.1\r\nX-Padding: " + "a".repeat(Http.MAX_HEAD_BYTES) + "\r\n\r\n"

        val error = assertThrows(BadRequestException::class.java) { read(huge) }

        assertThat(error.status).isEqualTo(Http.HEADERS_TOO_LARGE)
    }

    private fun read(raw: String): HttpRequest? =
        Http.readRequest(raw.toByteArray(Charsets.ISO_8859_1).inputStream())
}
