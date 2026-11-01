package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.WaypointMarker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class WaypointMarkersTest {
    @Test
    fun `no waypoints is an empty collection, not an error`() {
        val collection = Json.parseToJsonElement(WaypointMarkers.geoJson(emptyList())).jsonObject

        assertThat(collection["type"]!!.jsonPrimitive.content).isEqualTo("FeatureCollection")
        assertThat(collection["features"]!!.jsonArray).isEmpty()
    }

    @Test
    fun `each waypoint is a point in longitude-latitude order, carrying its id`() {
        val markers =
            listOf(
                WaypointMarker("w-1", LatLng(9.0301, 38.7402), "water_point"),
                WaypointMarker("w-2", LatLng(8.5, 39.25), null),
            )

        val features = features(WaypointMarkers.geoJson(markers))

        assertThat(features).hasSize(2)
        val first = features[0]
        val geometry = first["geometry"]!!.jsonObject
        assertThat(geometry["type"]!!.jsonPrimitive.content).isEqualTo("Point")
        assertThat(geometry["coordinates"]!!.jsonArray.map { it.jsonPrimitive.double })
            .containsExactly(38.7402, 9.0301)
            .inOrder()
        val ids =
            features.map {
                it["properties"]!!.jsonObject[WaypointMarkers.PROPERTY_ID]!!.jsonPrimitive.content
            }
        assertThat(ids).containsExactly("w-1", "w-2").inOrder()
    }

    private fun features(geoJson: String): List<JsonObject> = Json
        .parseToJsonElement(geoJson)
        .jsonObject["features"]!!
        .jsonArray
        .map { it.jsonObject }
}
