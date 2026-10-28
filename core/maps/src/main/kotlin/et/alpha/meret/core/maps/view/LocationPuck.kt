package et.alpha.meret.core.maps.view

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.geo.DevicePosition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Colours of the puck as `#RRGGBB`, taken from the theme by [OfflineMap]. */
internal data class PuckColours(
    val live: String,
    val stale: String,
    val halo: String,
)

/**
 * The location puck as GeoJSON for one source: a dot at the position and, around it, the
 * accuracy circle as a true ground-sized polygon, so it needs no redraw on zoom.
 *
 * Each feature carries the colour it is drawn in, so the layers stay fixed and only the
 * source data changes per fix.
 */
internal object LocationPuck {
    const val SOURCE_ID = "meret-location"
    const val ACCURACY_LAYER_ID = "meret-location-accuracy"
    const val DOT_LAYER_ID = "meret-location-dot"

    const val PROPERTY_KIND = "kind"
    const val PROPERTY_COLOUR = "colour"
    const val PROPERTY_OPACITY = "opacity"
    const val KIND_ACCURACY = "accuracy"
    const val KIND_DOT = "dot"

    /** Enough vertices that the circle reads as round at the zoom where it is a few cm wide. */
    const val CIRCLE_VERTICES = 48

    const val LIVE_OPACITY = 1.0
    const val STALE_OPACITY = 0.6

    /** An empty collection when [position] is null: the puck disappears, the layers stay. */
    fun geoJson(
        position: DevicePosition?,
        colours: PuckColours,
    ): String {
        val features =
            buildJsonArray {
                if (position != null) {
                    val colour = if (position.isLive) colours.live else colours.stale
                    val opacity = if (position.isLive) LIVE_OPACITY else STALE_OPACITY
                    if (position.accuracyMetres > 0.0) {
                        add(feature(accuracyRing(position), KIND_ACCURACY, colour, opacity))
                    }
                    add(feature(point(position), KIND_DOT, colour, opacity))
                }
            }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    /** `#RRGGBB` for an ARGB colour; alpha is dropped, layers set their own opacity. */
    fun hex(argb: Int): String = "#%06X".format(argb and RGB_MASK)

    private fun feature(
        geometry: JsonObject,
        kind: String,
        colour: String,
        opacity: Double,
    ): JsonObject = buildJsonObject {
        put("type", "Feature")
        put("geometry", geometry)
        put(
            "properties",
            buildJsonObject {
                put(PROPERTY_KIND, kind)
                put(PROPERTY_COLOUR, colour)
                put(PROPERTY_OPACITY, opacity)
            },
        )
    }

    private fun point(position: DevicePosition): JsonObject = buildJsonObject {
        put("type", "Point")
        put("coordinates", coordinates(position.position.longitude, position.position.latitude))
    }

    private fun accuracyRing(position: DevicePosition): JsonObject {
        val ring =
            buildJsonArray {
                for (step in 0..CIRCLE_VERTICES) {
                    // The last vertex repeats the first: GeoJSON rings are closed.
                    val bearing = FULL_TURN * (step % CIRCLE_VERTICES) / CIRCLE_VERTICES
                    val vertex =
                        GeoMath.destinationPoint(
                            from = position.position,
                            distanceMetres = position.accuracyMetres,
                            bearingDegrees = bearing,
                        )
                    add(coordinates(vertex.longitude, vertex.latitude))
                }
            }
        return buildJsonObject {
            put("type", "Polygon")
            put("coordinates", JsonArray(listOf(ring)))
        }
    }

    private fun coordinates(
        longitude: Double,
        latitude: Double,
    ): JsonArray = JsonArray(listOf(JsonPrimitive(longitude), JsonPrimitive(latitude)))

    private const val FULL_TURN = 360.0
    private const val RGB_MASK = 0xFFFFFF
}
