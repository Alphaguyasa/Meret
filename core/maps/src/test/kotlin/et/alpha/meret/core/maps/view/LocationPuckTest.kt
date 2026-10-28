package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class LocationPuckTest {
    private val colours = PuckColours(live = "#1D4ED8", stale = "#64748B", halo = "#FFFFFF")

    @Test
    fun `no position draws nothing`() {
        val features = features(LocationPuck.geoJson(null, colours))

        assertThat(features).isEmpty()
    }

    @Test
    fun `a live fix is a dot in the live colour over its accuracy circle`() {
        val features = features(LocationPuck.geoJson(HERE, colours))

        assertThat(features.map { it.property(LocationPuck.PROPERTY_KIND) })
            .containsExactly(LocationPuck.KIND_ACCURACY, LocationPuck.KIND_DOT)
            .inOrder()
        assertThat(features.map { it.property(LocationPuck.PROPERTY_COLOUR) })
            .containsExactly("#1D4ED8", "#1D4ED8")

        val dot = features.last().coordinates()
        assertThat(dot).containsExactly(38.74, 9.03).inOrder()
    }

    @Test
    fun `the accuracy circle is a closed ring at the accuracy distance`() {
        val accuracy = features(LocationPuck.geoJson(HERE, colours)).first()
        val ring =
            accuracy["geometry"]!!
                .jsonObject["coordinates"]!!
                .jsonArray
                .single()
                .jsonArray
                .map { vertex -> vertex.jsonArray.map { it.jsonPrimitive.double } }

        assertThat(ring).hasSize(LocationPuck.CIRCLE_VERTICES + 1)
        assertThat(ring.first()).isEqualTo(ring.last())
        ring.forEach { (longitude, latitude) ->
            val distance = GeoMath.haversineDistance(HERE.position, LatLng(latitude, longitude))
            assertThat(distance).isWithin(0.01).of(HERE.accuracyMetres)
        }
    }

    @Test
    fun `a cached fix is drawn in the stale colour and fainter`() {
        val stale = HERE.copy(isLive = false)

        val dot = features(LocationPuck.geoJson(stale, colours)).last()

        assertThat(dot.property(LocationPuck.PROPERTY_COLOUR)).isEqualTo("#64748B")
        assertThat(dot.property(LocationPuck.PROPERTY_OPACITY).toDouble())
            .isLessThan(LocationPuck.LIVE_OPACITY)
    }

    @Test
    fun `a fix with no accuracy radius is a dot alone`() {
        val exact = HERE.copy(accuracyMetres = 0.0)

        val features = features(LocationPuck.geoJson(exact, colours))

        assertThat(features.map { it.property(LocationPuck.PROPERTY_KIND) })
            .containsExactly(LocationPuck.KIND_DOT)
    }

    @Test
    fun `theme colours become hex without alpha`() {
        assertThat(LocationPuck.hex(0x801D4ED8.toInt())).isEqualTo("#1D4ED8")
        assertThat(LocationPuck.hex(0xFF000000.toInt())).isEqualTo("#000000")
    }

    private fun features(geoJson: String): List<JsonObject> {
        val collection = Json.parseToJsonElement(geoJson).jsonObject
        assertThat(collection["type"]!!.jsonPrimitive.content).isEqualTo("FeatureCollection")
        return collection["features"]!!.jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.property(name: String): String =
        this["properties"]!!.jsonObject[name]!!.jsonPrimitive.content

    private fun JsonObject.coordinates(): List<Double> =
        this["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.jsonPrimitive.double }

    private companion object {
        val HERE = DevicePosition(LatLng(9.03, 38.74), accuracyMetres = 12.0, isLive = true)
    }
}
