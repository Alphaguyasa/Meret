package et.alpha.meret.core.model.geo

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * A measured ring as the GeoJSON Polygon a saved measurement stores: longitude first, the
 * first position repeated last as GeoJSON requires. In memory a ring is open.
 */
public object RingGeoJson {
    private const val TYPE = "type"
    private const val POLYGON = "Polygon"
    private const val COORDINATES = "coordinates"

    /** [vertices], an open ring (a ring already closed is not closed twice), as GeoJSON. */
    public fun encode(vertices: List<LatLng>): String {
        val closed =
            if (vertices.firstOrNull() == vertices.lastOrNull()) {
                vertices
            } else {
                vertices + vertices.first()
            }
        val ring =
            closed.map {
                JsonArray(listOf(JsonPrimitive(it.longitude), JsonPrimitive(it.latitude)))
            }
        return JsonObject(
            mapOf(
                TYPE to JsonPrimitive(POLYGON),
                COORDINATES to JsonArray(listOf(JsonArray(ring))),
            ),
        ).toString()
    }

    /** The open ring [text] holds, or null when it is not a one-ring GeoJSON Polygon. */
    public fun decode(text: String): List<LatLng>? = parse(text)
        ?.takeIf { (it[TYPE] as? JsonPrimitive)?.content == POLYGON }
        ?.let { it[COORDINATES] as? JsonArray }
        ?.singleOrNull()
        ?.let { it as? JsonArray }
        ?.let(::positions)
        ?.let(::open)

    private fun parse(text: String): JsonObject? = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

    private fun open(ring: List<LatLng>): List<LatLng> =
        if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring

    private fun positions(ring: JsonArray): List<LatLng>? {
        val read = ring.map(::position)
        return if (null in read) null else read.filterNotNull()
    }

    private fun position(element: JsonElement): LatLng? {
        val numbers = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull }
        val longitude = numbers?.getOrNull(0)
        val latitude = numbers?.getOrNull(1)
        return if (longitude != null && latitude != null) LatLng(latitude, longitude) else null
    }
}
