package et.alpha.meret.core.maps.view

import et.alpha.meret.core.model.geo.WaypointMarker
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Saved waypoints as GeoJSON for one clustered source: a `Point` per waypoint, carrying its id
 * so a later tap can find it. MapLibre groups nearby points into clusters on its own thread,
 * so a few thousand waypoints cost one string, not one layer each.
 */
internal object WaypointMarkers {
    const val SOURCE_ID = "meret-waypoints"
    const val CLUSTER_LAYER_ID = "meret-waypoints-cluster"
    const val COUNT_LAYER_ID = "meret-waypoints-count"
    const val POINT_LAYER_ID = "meret-waypoints-point"
    const val PROPERTY_ID = "id"

    /** Set by MapLibre on a cluster feature; absent on a single waypoint. */
    const val PROPERTY_POINT_COUNT = "point_count"
    const val PROPERTY_POINT_COUNT_ABBREVIATED = "point_count_abbreviated"

    /** An empty collection for no waypoints: the markers disappear, the layers stay. */
    fun geoJson(markers: List<WaypointMarker>): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(markers.map(::feature)))
    }.toString()

    private fun feature(marker: WaypointMarker): JsonObject = buildJsonObject {
        put("type", "Feature")
        put(
            "geometry",
            buildJsonObject {
                put("type", "Point")
                put(
                    "coordinates",
                    JsonArray(
                        listOf(
                            JsonPrimitive(marker.position.longitude),
                            JsonPrimitive(marker.position.latitude),
                        ),
                    ),
                )
            },
        )
        put("properties", buildJsonObject { put(PROPERTY_ID, marker.id) })
    }
}
