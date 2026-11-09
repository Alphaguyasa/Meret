package et.alpha.meret.core.maps.view

import et.alpha.meret.core.model.geo.LatLng
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The plot being measured as GeoJSON for one source: a dot at each corner, the line joining
 * them (closed from three corners on), and the shape filled from three corners on. The three
 * kinds are told apart by [PROPERTY_KIND], so one source feeds one layer of each.
 */
internal object MeasureRing {
    const val SOURCE_ID = "meret-measure-ring"
    const val FILL_LAYER_ID = "meret-measure-ring-fill"
    const val LINE_LAYER_ID = "meret-measure-ring-line"
    const val CORNER_LAYER_ID = "meret-measure-ring-corner"
    const val PROPERTY_KIND = "kind"
    const val KIND_FILL = "fill"
    const val KIND_LINE = "line"
    const val KIND_CORNER = "corner"

    private const val MIN_CORNERS_FOR_LINE = 2
    private const val MIN_CORNERS_FOR_FILL = 3

    /** An empty collection for no corners: the ring disappears, the layers stay. */
    fun geoJson(vertices: List<LatLng>): String {
        val closed =
            if (vertices.size >= MIN_CORNERS_FOR_FILL) vertices + vertices.first() else vertices
        val features =
            buildList {
                if (vertices.size >= MIN_CORNERS_FOR_FILL) {
                    add(feature(KIND_FILL, "Polygon", JsonArray(listOf(positions(closed)))))
                }
                if (vertices.size >= MIN_CORNERS_FOR_LINE) {
                    add(feature(KIND_LINE, "LineString", positions(closed)))
                }
                vertices.forEach { add(feature(KIND_CORNER, "Point", position(it))) }
            }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }.toString()
    }

    private fun feature(
        kind: String,
        geometryType: String,
        coordinates: JsonElement,
    ): JsonObject = buildJsonObject {
        put("type", "Feature")
        put(
            "geometry",
            buildJsonObject {
                put("type", geometryType)
                put("coordinates", coordinates)
            },
        )
        put("properties", buildJsonObject { put(PROPERTY_KIND, kind) })
    }

    private fun positions(points: List<LatLng>): JsonArray = JsonArray(points.map(::position))

    private fun position(point: LatLng): JsonArray =
        JsonArray(listOf(JsonPrimitive(point.longitude), JsonPrimitive(point.latitude)))
}
