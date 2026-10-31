package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class LiveTrackLineTest {
    @Test
    fun `nothing recording draws nothing`() {
        assertThat(features(LiveTrackLine.geoJson(null))).isEmpty()
        assertThat(features(LiveTrackLine.geoJson(TrackLine(emptyList())))).isEmpty()
    }

    @Test
    fun `each segment is its own line, in longitude-latitude order`() {
        val line =
            TrackLine(
                listOf(
                    listOf(LatLng(9.0, 38.0), LatLng(9.1, 38.0)),
                    listOf(LatLng(9.2, 38.1), LatLng(9.3, 38.1), LatLng(9.3, 38.2)),
                ),
            )

        val geometry = features(LiveTrackLine.geoJson(line)).single()["geometry"]!!.jsonObject

        assertThat(geometry["type"]!!.jsonPrimitive.content).isEqualTo("MultiLineString")
        val lines = geometry["coordinates"]!!.jsonArray.map { it.coordinates() }
        assertThat(lines)
            .containsExactly(
                listOf(listOf(38.0, 9.0), listOf(38.0, 9.1)),
                listOf(listOf(38.1, 9.2), listOf(38.1, 9.3), listOf(38.2, 9.3)),
            ).inOrder()
    }

    @Test
    fun `a segment of one fix is left out until it has a second`() {
        val single = TrackLine(listOf(listOf(LatLng(9.0, 38.0))))
        val mixed =
            TrackLine(
                listOf(
                    listOf(LatLng(9.0, 38.0)),
                    listOf(LatLng(9.1, 38.0), LatLng(9.2, 38.0)),
                ),
            )

        assertThat(features(LiveTrackLine.geoJson(single))).isEmpty()
        val geometry = features(LiveTrackLine.geoJson(mixed)).single()["geometry"]!!.jsonObject
        assertThat(geometry["coordinates"]!!.jsonArray).hasSize(1)
    }

    @Test
    fun `earlier tracks are one feature each, and a track with nothing to draw is left out`() {
        val first = TrackLine(listOf(listOf(LatLng(9.0, 38.0), LatLng(9.1, 38.0))))
        val empty = TrackLine(listOf(listOf(LatLng(9.5, 38.5))))
        val second = TrackLine(listOf(listOf(LatLng(8.0, 37.0), LatLng(8.1, 37.1))))

        val drawn = features(EarlierTrackLines.geoJson(listOf(first, empty, second)))

        val firstPoints = drawn.map { it["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0] }
        assertThat(firstPoints.map { it.coordinates().first() })
            .containsExactly(listOf(38.0, 9.0), listOf(37.0, 8.0))
            .inOrder()
        assertThat(features(EarlierTrackLines.geoJson(emptyList()))).isEmpty()
    }

    private fun features(json: String): List<JsonObject> {
        val collection = Json.parseToJsonElement(json).jsonObject
        assertThat(collection["type"]!!.jsonPrimitive.content).isEqualTo("FeatureCollection")
        return collection["features"]!!.jsonArray.map { it.jsonObject }
    }

    private fun JsonElement.coordinates(): List<List<Double>> =
        jsonArray.map { pair -> pair.jsonArray.map { it.jsonPrimitive.double } }
}
