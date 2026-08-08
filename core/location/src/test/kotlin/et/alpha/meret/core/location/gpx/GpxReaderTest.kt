package et.alpha.meret.core.location.gpx

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.Traces
import org.junit.Test

class GpxReaderTest {
    @Test
    fun `reads every point of a fixture`() {
        val trace = read(Traces.CLEAN_WALK_1KM)

        assertThat(trace.points).hasSize(Traces.CLEAN_WALK_POINTS)
        assertThat(trace.name).isEqualTo("Clean 1 km walk")
    }

    @Test
    fun `reads coordinates, time and the accuracy extension`() {
        val first = read(Traces.CLEAN_WALK_1KM).points.first()

        assertThat(first.position.latitude).isWithin(1e-7).of(8.9806188)
        assertThat(first.position.longitude).isWithin(1e-7).of(38.7577985)
        assertThat(first.accuracyMetres).isEqualTo(4.0)
        assertThat(first.timeMillis).isEqualTo(BASE_TIME_MILLIS)
    }

    @Test
    fun `the metadata name does not overwrite the track name`() {
        // <name> appears in <metadata>, on the <trk>, and on every waypoint. Only the track's
        // own name is wanted.
        assertThat(read(Traces.WALK_WITH_GAP).name).isEqualTo("Walk with a 5 minute gap")
    }

    @Test
    fun `a single trkseg is one segment`() {
        val trace = read(Traces.WALK_WITH_GAP)

        // The five-minute gap is deliberately inside one <trkseg>, so the file structure says
        // one segment and the break has to be inferred from the timestamps downstream.
        assertThat(trace.segmentCount).isEqualTo(1)
        assertThat(trace.points.map { it.segmentIndex }.distinct()).containsExactly(0)
    }

    @Test
    fun `separate trksegs become separate segments`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk><name>Two</name>
                  <trkseg>
                    <trkpt lat="8.98" lon="38.75"><time>2026-03-14T07:30:00Z</time></trkpt>
                    <trkpt lat="8.99" lon="38.76"><time>2026-03-14T07:30:01Z</time></trkpt>
                  </trkseg>
                  <trkseg>
                    <trkpt lat="9.10" lon="38.80"><time>2026-03-14T07:40:00Z</time></trkpt>
                  </trkseg>
                </trk></gpx>
                """.trimIndent(),
            )

        assertThat(trace.segmentCount).isEqualTo(2)
        assertThat(trace.points.map { it.segmentIndex }).containsExactly(0, 0, 1).inOrder()
    }

    @Test
    fun `reads elevation and hdop when present`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk><trkseg>
                  <trkpt lat="8.98" lon="38.75">
                    <ele>2355.4</ele>
                    <hdop>1.8</hdop>
                  </trkpt>
                </trkseg></trk></gpx>
                """.trimIndent(),
            )

        val point = trace.points.single()
        assertThat(point.elevationMetres).isEqualTo(2355.4)
        assertThat(point.horizontalDilutionOfPrecision).isEqualTo(1.8)
        assertThat(point.accuracyMetres).isNull()
    }

    @Test
    fun `parses an offset timestamp as well as a zulu one`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk><trkseg>
                  <trkpt lat="8.98" lon="38.75"><time>2026-03-14T10:30:00+03:00</time></trkpt>
                  <trkpt lat="8.99" lon="38.76"><time>2026-03-14T07:30:00Z</time></trkpt>
                </trkseg></trk></gpx>
                """.trimIndent(),
            )

        // Ethiopia is UTC+3, so an exporter writing local time is the common case, and both
        // forms have to land on the same instant.
        assertThat(trace.points[0].timeMillis).isEqualTo(trace.points[1].timeMillis)
    }

    @Test
    fun `an unparseable timestamp yields null rather than failing the trace`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk><trkseg>
                  <trkpt lat="8.98" lon="38.75"><time>not a date</time></trkpt>
                </trkseg></trk></gpx>
                """.trimIndent(),
            )

        // A trace with unreadable times is still a usable path; ReplayLocationEngine
        // synthesises timing from the profile interval instead.
        assertThat(trace.points.single().timeMillis).isNull()
    }

    @Test
    fun `a trkpt outside any trkseg is treated as segment zero`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk>
                  <trkpt lat="8.98" lon="38.75"/>
                </trk></gpx>
                """.trimIndent(),
            )

        assertThat(trace.points.single().segmentIndex).isEqualTo(0)
    }

    @Test
    fun `points with missing or nonsensical coordinates are skipped`() {
        val trace =
            parse(
                """
                <gpx version="1.1"><trk><trkseg>
                  <trkpt lat="8.98" lon="38.75"/>
                  <trkpt lon="38.76"/>
                  <trkpt lat="999" lon="38.77"/>
                  <trkpt lat="8.99" lon="38.78"/>
                </trkseg></trk></gpx>
                """.trimIndent(),
            )

        // Skipping beats refusing: GPX in the wild contains malformed elements, and losing a
        // whole 5,000-point trace over one of them is the worse outcome.
        assertThat(trace.points).hasSize(2)
    }

    @Test
    fun `waypoints and routes are ignored`() {
        val trace =
            parse(
                """
                <gpx version="1.1">
                  <wpt lat="1.0" lon="2.0"><name>A waypoint</name></wpt>
                  <rte><rtept lat="3.0" lon="4.0"/></rte>
                  <trk><name>The track</name><trkseg>
                    <trkpt lat="8.98" lon="38.75"/>
                  </trkseg></trk>
                </gpx>
                """.trimIndent(),
            )

        assertThat(trace.points).hasSize(1)
        assertThat(trace.name).isEqualTo("The track")
        assertThat(
            trace.points
                .single()
                .position.latitude,
        ).isWithin(1e-9).of(8.98)
    }

    @Test
    fun `malformed xml is reported as a parse failure`() {
        val failure =
            runCatching { parse("<gpx><trk><trkseg>") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GpxParseException::class.java)
    }

    @Test
    fun `a file with no track points is reported as a parse failure`() {
        val failure =
            runCatching { parse("<gpx version=\"1.1\"></gpx>") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GpxParseException::class.java)
    }

    /**
     * A GPX file can arrive from a peer device or a supervisor's export, so it is untrusted
     * input. An external entity declaration in one must not turn into a file read.
     */
    @Test
    fun `a doctype declaration is refused`() {
        val failure =
            runCatching {
                parse(
                    """
                    <!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                    <gpx version="1.1"><trk><trkseg>
                      <trkpt lat="8.98" lon="38.75"/>
                    </trkseg></trk></gpx>
                    """.trimIndent(),
                )
            }.exceptionOrNull()

        assertThat(failure).isInstanceOf(GpxParseException::class.java)
    }

    @Test
    fun `a namespaced gpx parses the same as a bare one`() {
        val trace =
            parse(
                """
                <gpx:gpx version="1.1" xmlns:gpx="http://www.topografix.com/GPX/1/1">
                  <gpx:trk><gpx:name>Prefixed</gpx:name><gpx:trkseg>
                    <gpx:trkpt lat="8.98" lon="38.75"><gpx:ele>2000</gpx:ele></gpx:trkpt>
                  </gpx:trkseg></gpx:trk>
                </gpx:gpx>
                """.trimIndent(),
            )

        // Exporters differ on whether they prefix elements; the reader strips any prefix rather
        // than requiring one form.
        assertThat(trace.name).isEqualTo("Prefixed")
        assertThat(trace.points.single().elevationMetres).isEqualTo(2000.0)
    }

    private fun read(name: String): GpxTrace = Traces.open(name).use { GpxReader.read(it) }

    private fun parse(xml: String): GpxTrace = xml.byteInputStream().use { GpxReader.read(it) }

    private companion object {
        /** 2026-03-14T07:30:00Z, the base time in `tools/fixtures/gen-traces.js`. */
        const val BASE_TIME_MILLIS = 1_773_473_400_000L
    }
}
