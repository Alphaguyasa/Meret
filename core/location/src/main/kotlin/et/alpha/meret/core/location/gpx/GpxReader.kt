package et.alpha.meret.core.location.gpx

import et.alpha.meret.core.model.geo.LatLng
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.xml.parsers.SAXParserFactory

/** One `<trkpt>`. Everything except the coordinate is optional, because in the wild it is. */
public data class GpxTrackPoint(
    public val position: LatLng,
    /** Zero-based `<trkseg>` ordinal. A new segment means the line should break. */
    public val segmentIndex: Int,
    public val elevationMetres: Double? = null,
    /** Wall clock from `<time>`, epoch millis. */
    public val timeMillis: Long? = null,
    public val horizontalDilutionOfPrecision: Double? = null,
    /**
     * Horizontal accuracy in metres, from a `<accuracy>` element inside `<extensions>`.
     *
     * Not part of the GPX schema — GPX predates the idea that a consumer might care. Our own
     * fixture traces write it because a replay test needs to control the accuracy the
     * validity gate and the Kalman filter see, and deriving it from HDOP would make the
     * fixtures depend on a fudge factor rather than on a stated number.
     */
    public val accuracyMetres: Double? = null,
)

public data class GpxTrace(
    public val name: String?,
    public val points: List<GpxTrackPoint>,
) {
    public val segmentCount: Int
        get() = points.maxOfOrNull { it.segmentIndex + 1 } ?: 0
}

/** A GPX file that could not be read. Malformed XML, or no track points in it. */
public class GpxParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Reads the track out of a GPX 1.1 file.
 *
 * SAX, via [SAXParserFactory], deliberately. It is the only XML API present on both the
 * Android runtime and a desktop JVM: `javax.xml.stream` is absent on Android, and
 * `XmlPullParser` is an Android stub that throws in a plain unit test. Since the whole
 * point of `ReplayLocationEngine` is that tracking is testable without a device, the parser
 * it depends on has to run without one.
 *
 * Streaming rather than DOM: a day's exported track is a hundred thousand `<trkpt>`
 * elements, and building a document tree for it on a low-end device would be the largest
 * single allocation the app ever makes.
 *
 * Only `<trk>` is read. `<wpt>` and `<rte>` are ignored — a replay needs an ordered,
 * timestamped path, and neither of those is one.
 */
public object GpxReader {
    /**
     * Parses [input] and returns its track. Does **not** close the stream; the caller owns
     * it.
     *
     * @throws GpxParseException if the XML is malformed or contains no `<trkpt>`.
     */
    public fun read(input: InputStream): GpxTrace {
        val handler = parse(input)

        if (handler.points.isEmpty()) {
            throw GpxParseException("GPX contains no <trkpt> elements")
        }

        return GpxTrace(name = handler.trackName, points = handler.points)
    }

    /** Runs the SAX parse, translating both failure modes into [GpxParseException]. */
    private fun parse(input: InputStream): GpxHandler {
        val handler = GpxHandler()

        try {
            // Namespace-aware parsing off, and DOCTYPEs rejected outright. A GPX file can
            // arrive from a peer device or a supervisor's export, so it is untrusted input; an
            // external entity declaration in one must not become a file read.
            val factory =
                SAXParserFactory.newInstance().apply {
                    isNamespaceAware = false
                    setFeature(FEATURE_DISALLOW_DOCTYPE, true)
                    setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false)
                    setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false)
                }
            factory.newSAXParser().parse(input, handler)
        } catch (e: SAXException) {
            throw GpxParseException("Malformed GPX: ${e.message}", e)
        } catch (e: IOException) {
            throw GpxParseException("Could not read GPX: ${e.message}", e)
        }

        return handler
    }

    private const val FEATURE_DISALLOW_DOCTYPE =
        "http://apache.org/xml/features/disallow-doctype-decl"
    private const val FEATURE_EXTERNAL_GENERAL_ENTITIES =
        "http://xml.org/sax/features/external-general-entities"
    private const val FEATURE_EXTERNAL_PARAMETER_ENTITIES =
        "http://xml.org/sax/features/external-parameter-entities"
}

/**
 * Accumulating SAX handler.
 *
 * Text is gathered into a buffer across [characters] calls rather than read once: SAX is
 * free to split a text node at any boundary, and it does so in practice on long
 * coordinates, which would silently truncate an elevation to its first three digits.
 */
private class GpxHandler : DefaultHandler() {
    val points = mutableListOf<GpxTrackPoint>()
    var trackName: String? = null
        private set

    private val text = StringBuilder()
    private var segmentIndex = -1
    private var insideTrack = false
    private var insideMetadata = false

    private var latitude: Double? = null
    private var longitude: Double? = null
    private var elevation: Double? = null
    private var timeMillis: Long? = null
    private var hdop: Double? = null
    private var accuracy: Double? = null

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String,
        attributes: Attributes,
    ) {
        text.setLength(0)

        when (qName.substringAfterLast(':').lowercase()) {
            "trk" -> insideTrack = true
            "metadata" -> insideMetadata = true
            "trkseg" -> segmentIndex++
            "trkpt" -> beginPoint(attributes)
        }
    }

    override fun characters(
        ch: CharArray,
        start: Int,
        length: Int,
    ) {
        text.appendRange(ch, start, start + length)
    }

    override fun endElement(
        uri: String?,
        localName: String?,
        qName: String,
    ) {
        val value = text.toString().trim()

        when (qName.substringAfterLast(':').lowercase()) {
            "trk" -> insideTrack = false

            "metadata" -> insideMetadata = false

            "trkpt" -> endPoint()

            "ele" -> elevation = value.toDoubleOrNull()

            "time" -> timeMillis = parseGpxTime(value)

            "hdop" -> hdop = value.toDoubleOrNull()

            "accuracy" -> accuracy = value.toDoubleOrNull()

            // `<name>` appears in metadata, on the track, and on every waypoint. Only the
            // track's own name is wanted, and metadata's must not overwrite it.
            "name" -> if (insideTrack && !insideMetadata && trackName == null) trackName = value
        }

        text.setLength(0)
    }

    private fun beginPoint(attributes: Attributes) {
        // A <trkpt> outside any <trkseg> is technically invalid but common in files written
        // by hand. Treat it as segment 0 rather than dropping the whole track.
        if (segmentIndex < 0) segmentIndex = 0

        latitude = attributes.getValue("lat")?.toDoubleOrNull()
        longitude = attributes.getValue("lon")?.toDoubleOrNull()
        elevation = null
        timeMillis = null
        hdop = null
        accuracy = null
    }

    private fun endPoint() {
        val lat = latitude
        val lon = longitude
        // Silently skipping a coordinate-less point is right here: GPX in the wild contains
        // them, and refusing to replay a 5,000-point trace over one malformed element would
        // be a worse outcome than replaying 4,999.
        if (lat == null || lon == null) return

        // `LatLng` is deliberately unvalidated because a *fix* is stored as reported and
        // judged by the validity gate. A GPX attribute of `lat="999"` is a different thing:
        // a malformed file, not an observation, and there is nothing to audit about it.
        val position = LatLng(lat, lon)
        if (!position.isValid) return

        points +=
            GpxTrackPoint(
                position = position,
                segmentIndex = segmentIndex,
                elevationMetres = elevation,
                timeMillis = timeMillis,
                horizontalDilutionOfPrecision = hdop,
                accuracyMetres = accuracy,
            )

        latitude = null
        longitude = null
    }
}

/**
 * Parses a GPX timestamp.
 *
 * Tries an offset-bearing form first, then a bare instant. Anything else yields `null`
 * rather than throwing: a trace with unparseable times is still a usable path, and
 * `ReplayLocationEngine` can synthesise timing from its configured interval.
 */
private fun parseGpxTime(value: String): Long? {
    if (value.isEmpty()) return null

    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
