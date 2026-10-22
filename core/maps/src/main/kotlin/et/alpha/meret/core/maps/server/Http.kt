package et.alpha.meret.core.maps.server

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * The slice of HTTP/1.1 the loopback server needs: GET and HEAD, no request bodies,
 * persistent connections. Its one client is MapLibre's OkHttp on the same phone.
 */
internal class HttpRequest(
    val method: String,
    /** The path without its query string, still percent-encoded. */
    val path: String,
    val keepAlive: Boolean,
    private val headers: Map<String, String>,
) {
    /** Header lookup; names are case-insensitive. */
    fun header(name: String): String? = headers[name.lowercase()]

    /** Whether the client accepts a gzip body as is. OkHttp always sends `gzip`. */
    val acceptsGzip: Boolean
        get() =
            header("accept-encoding").orEmpty().split(',').any { coding ->
                val parts = coding.split(';').map { it.trim().lowercase() }
                parts.first() == "gzip" && parts.drop(1).none { it.replace(" ", "") == "q=0" }
            }
}

/** A request the server answers with [status] and then hangs up. */
internal class BadRequestException(
    val status: Int,
) : Exception("HTTP $status")

internal class HttpResponse(
    val status: Int,
    val body: ByteArray = ByteArray(0),
    val contentType: String? = null,
    val contentEncoding: String? = null,
)

internal object Http {
    const val OK = 200
    const val NO_CONTENT = 204
    const val BAD_REQUEST = 400
    const val NOT_FOUND = 404
    const val METHOD_NOT_ALLOWED = 405
    const val HEADERS_TOO_LARGE = 431
    const val INTERNAL_ERROR = 500
    const val SERVICE_UNAVAILABLE = 503

    /** Request line plus headers. MapLibre's requests are a few hundred bytes. */
    const val MAX_HEAD_BYTES = 8 * 1024

    private const val CR = '\r'.code
    private const val LF = '\n'.code

    /**
     * Reads one request head, or returns null when the client closed the connection
     * cleanly before sending anything.
     *
     * Throws [BadRequestException] for anything this server will not parse: an oversized
     * head, a malformed line, or a request with a body.
     */
    fun readRequest(input: InputStream): HttpRequest? {
        val lines = readHead(input) ?: return null
        val requestLine = lines.first().split(' ')
        ensure(requestLine.size == REQUEST_LINE_PARTS)
        val (method, target, version) = requestLine
        ensure(target.startsWith('/') && version.startsWith("HTTP/1."))
        val headers = parseHeaders(lines.drop(1))
        val hasBody =
            headers["transfer-encoding"] != null ||
                (headers["content-length"]?.trim() ?: "0") != "0"
        ensure(!hasBody)
        val connection = headers["connection"].orEmpty().lowercase()
        val keepAlive =
            if (version == "HTTP/1.0") "keep-alive" in connection else "close" !in connection
        return HttpRequest(method, target.substringBefore('?'), keepAlive, headers)
    }

    fun writeResponse(
        output: OutputStream,
        response: HttpResponse,
        keepAlive: Boolean,
        includeBody: Boolean,
    ) {
        val head =
            buildString {
                append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
                response.contentType?.let { append("Content-Type: $it\r\n") }
                response.contentEncoding?.let { append("Content-Encoding: $it\r\n") }
                append("Content-Length: ${response.body.size}\r\n")
                // The port changes every launch, so a cached copy is never read again.
                append("Cache-Control: no-store\r\n")
                if (response.status == METHOD_NOT_ALLOWED) append("Allow: GET, HEAD\r\n")
                append(if (keepAlive) "Connection: keep-alive\r\n" else "Connection: close\r\n")
                append("\r\n")
            }
        output.write(head.toByteArray(Charsets.US_ASCII))
        if (includeBody) output.write(response.body)
        output.flush()
    }

    private fun readHead(input: InputStream): List<String>? {
        val buffer = ByteArrayOutputStream()
        var tail = 0 // Matched bytes of the CR LF CR LF terminator.
        while (tail < HEAD_TERMINATOR.size) {
            val byte = input.read()
            if (byte == -1 && buffer.size() == 0) return null
            ensure(byte != -1)
            ensure(buffer.size() < MAX_HEAD_BYTES, HEADERS_TOO_LARGE)
            buffer.write(byte)
            tail =
                when (byte) {
                    HEAD_TERMINATOR[tail] -> tail + 1
                    CR -> 1
                    else -> 0
                }
        }
        return buffer
            .toString(Charsets.ISO_8859_1.name())
            .removeSuffix("\r\n\r\n")
            .split("\r\n")
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> = lines.associate { line ->
        val colon = line.indexOf(':')
        ensure(colon > 0)
        line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
    }

    private fun ensure(
        condition: Boolean,
        status: Int = BAD_REQUEST,
    ) {
        if (!condition) throw BadRequestException(status)
    }

    private fun reason(status: Int): String = when (status) {
        OK -> "OK"
        NO_CONTENT -> "No Content"
        BAD_REQUEST -> "Bad Request"
        NOT_FOUND -> "Not Found"
        METHOD_NOT_ALLOWED -> "Method Not Allowed"
        HEADERS_TOO_LARGE -> "Request Header Fields Too Large"
        SERVICE_UNAVAILABLE -> "Service Unavailable"
        else -> "Internal Server Error"
    }

    private const val REQUEST_LINE_PARTS = 3
    private val HEAD_TERMINATOR = intArrayOf(CR, LF, CR, LF)
}
