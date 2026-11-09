package et.alpha.meret.core.model.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RingGeoJsonTest {
    private val ring =
        listOf(
            LatLng(9.03, 38.74),
            LatLng(9.0312, 38.7403),
            LatLng(9.031, 38.7415),
        )

    @Test
    fun `encodes a closed GeoJSON polygon, longitude first`() {
        assertThat(RingGeoJson.encode(ring)).isEqualTo(
            """{"type":"Polygon","coordinates":[[[38.74,9.03],[38.7403,9.0312],""" +
                """[38.7415,9.031],[38.74,9.03]]]}""",
        )
    }

    @Test
    fun `a ring round-trips open`() {
        assertThat(RingGeoJson.decode(RingGeoJson.encode(ring))).isEqualTo(ring)
    }

    @Test
    fun `a ring already closed is not closed twice`() {
        assertThat(RingGeoJson.encode(ring + ring.first())).isEqualTo(RingGeoJson.encode(ring))
    }

    @Test
    fun `text that is not a one-ring polygon decodes to null`() {
        assertThat(RingGeoJson.decode("not json")).isNull()
        assertThat(RingGeoJson.decode("[]")).isNull()
        assertThat(RingGeoJson.decode("""{"type":"Point","coordinates":[38.74,9.03]}""")).isNull()
        assertThat(RingGeoJson.decode("""{"type":"Polygon"}""")).isNull()
        assertThat(RingGeoJson.decode("""{"type":"Polygon","coordinates":[[],[]]}""")).isNull()
        assertThat(RingGeoJson.decode("""{"type":"Polygon","coordinates":[[[38.74]]]}""")).isNull()
        assertThat(RingGeoJson.decode("""{"type":"Polygon","coordinates":[[["a","b"]]]}"""))
            .isNull()
    }
}
