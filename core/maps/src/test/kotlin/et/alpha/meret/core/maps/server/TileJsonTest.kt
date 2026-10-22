package et.alpha.meret.core.maps.server

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class TileJsonTest {
    private val tilesUrl = "http://127.0.0.1:41234/ethiopia/{z}/{x}/{y}.pbf"

    @Test
    fun `a full pack becomes a full TileJSON document`() {
        val metadata =
            MbtilesMetadata.fromRows(
                mapOf(
                    "name" to "OpenMapTiles",
                    "format" to "pbf",
                    "minzoom" to "0",
                    "maxzoom" to "14",
                    "bounds" to "32.99,3.4,47.99,14.89",
                    "center" to "38.7578,9.03,6",
                    "attribution" to "© OpenStreetMap contributors",
                    "json" to """{"vector_layers":[{"id":"water","fields":{}}],"tilestats":{}}""",
                ),
            )

        assertThat(parse(TileJson.build(metadata, tilesUrl))).isEqualTo(
            parse(
                """
                {
                  "tilejson": "3.0.0",
                  "scheme": "xyz",
                  "tiles": ["$tilesUrl"],
                  "format": "pbf",
                  "minzoom": 0,
                  "maxzoom": 14,
                  "name": "OpenMapTiles",
                  "attribution": "© OpenStreetMap contributors",
                  "bounds": [32.99, 3.4, 47.99, 14.89],
                  "center": [38.7578, 9.03, 6],
                  "vector_layers": [{"id": "water", "fields": {}}]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `missing optional metadata is left out rather than written as null`() {
        val metadata =
            MbtilesMetadata.fromRows(
                mapOf(
                    "format" to "pbf",
                    "minzoom" to "0",
                    "maxzoom" to "6",
                ),
            )

        val json = parse(TileJson.build(metadata, tilesUrl))

        assertThat(
            json.keys,
        ).containsExactly("tilejson", "scheme", "tiles", "format", "minzoom", "maxzoom")
    }

    @Test
    fun `a broken json row drops vector_layers and keeps the document`() {
        for (row in listOf("{not json", "[]", """{"vector_layers":"water"}""")) {
            val metadata =
                MbtilesMetadata.fromRows(
                    mapOf("format" to "pbf", "minzoom" to "0", "maxzoom" to "6", "json" to row),
                )

            val json = parse(TileJson.build(metadata, tilesUrl))

            assertThat(json).doesNotContainKey("vector_layers")
            assertThat(json).containsKey("tiles")
        }
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
