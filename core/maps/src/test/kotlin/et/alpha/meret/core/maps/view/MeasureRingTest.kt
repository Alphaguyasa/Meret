package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class MeasureRingTest {
    private val a = LatLng(9.03, 38.74)
    private val b = LatLng(9.03, 38.75)
    private val c = LatLng(9.04, 38.75)

    private fun features(vertices: List<LatLng>): List<JsonObject> {
        val collection = Json.parseToJsonElement(MeasureRing.geoJson(vertices)).jsonObject
        return collection["features"]!!.jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.kind(): String =
        this["properties"]!!.jsonObject[MeasureRing.PROPERTY_KIND]!!.jsonPrimitive.content

    private fun JsonObject.coordinates(): JsonArray =
        this["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    @Test
    fun `no corners is an empty collection, not an error`() {
        assertThat(features(emptyList())).isEmpty()
    }

    @Test
    fun `one corner is a dot, two add the line between them, unclosed`() {
        assertThat(features(listOf(a)).map { it.kind() }).containsExactly(MeasureRing.KIND_CORNER)

        val two = features(listOf(a, b))
        val line = two.single { it.kind() == MeasureRing.KIND_LINE }

        assertThat(two.count { it.kind() == MeasureRing.KIND_CORNER }).isEqualTo(2)
        assertThat(two.none { it.kind() == MeasureRing.KIND_FILL }).isTrue()
        assertThat(line.coordinates()).hasSize(2)
    }

    @Test
    fun `three corners close the line and fill the shape, longitude first`() {
        val three = features(listOf(a, b, c))

        val fill = three.single { it.kind() == MeasureRing.KIND_FILL }
        val line = three.single { it.kind() == MeasureRing.KIND_LINE }
        val ring = fill.coordinates().single().jsonArray

        assertThat(ring).hasSize(4)
        assertThat(ring.first()).isEqualTo(ring.last())
        assertThat(line.coordinates()).hasSize(4)
        val firstPosition = line.coordinates().first().jsonArray
        assertThat(firstPosition[0].jsonPrimitive.double).isEqualTo(a.longitude)
        assertThat(firstPosition[1].jsonPrimitive.double).isEqualTo(a.latitude)
    }
}
