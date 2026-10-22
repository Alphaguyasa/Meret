package et.alpha.meret.core.maps.server

import et.alpha.meret.core.maps.mbtiles.MbtilesMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the TileJSON 3.0.0 document MapLibre reads before it asks for any tile.
 *
 * `vector_layers` comes from the pack's `json` metadata row. MapLibre Native renders without
 * it, so a pack whose row is missing or broken still gets a document, just without the list.
 */
internal object TileJson {
    fun build(
        metadata: MbtilesMetadata,
        tilesUrl: String,
    ): String {
        val document =
            buildJsonObject {
                put("tilejson", "3.0.0")
                put("scheme", "xyz")
                put("tiles", JsonArray(listOf(JsonPrimitive(tilesUrl))))
                put("format", metadata.format)
                put("minzoom", metadata.minZoom)
                put("maxzoom", metadata.maxZoom)
                metadata.name?.let { put("name", it) }
                metadata.attribution?.let { put("attribution", it) }
                metadata.bounds?.let {
                    put("bounds", numbers(it.west, it.south, it.east, it.north))
                }
                metadata.center?.let {
                    put("center", numbers(it.longitude, it.latitude, it.zoom))
                }
                vectorLayers(metadata.json)?.let { put("vector_layers", it) }
            }
        return document.toString()
    }

    private fun numbers(vararg values: Number): JsonArray =
        JsonArray(values.map { JsonPrimitive(it) })

    private fun vectorLayers(json: String?): JsonElement? {
        // Only JSON syntax errors reach here; the input is a string, not a stream.
        val parsed = json?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        return (parsed as? JsonObject)?.get("vector_layers") as? JsonArray
    }
}
