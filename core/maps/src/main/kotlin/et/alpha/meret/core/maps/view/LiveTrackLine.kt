package et.alpha.meret.core.maps.view

import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The track being recorded as GeoJSON for one source: a single `MultiLineString`, one line per
 * segment, so a pause leaves a gap rather than a straight line across unrecorded ground.
 */
internal object LiveTrackLine {
    const val SOURCE_ID = "meret-live-track"
    const val LAYER_ID = "meret-live-track-line"

    /**
     * An empty collection when [line] is null or has nothing to draw: the line disappears, the
     * layer stays. A segment of one point is left out until it has a second.
     */
    fun geoJson(line: TrackLine?): String = TrackLineGeoJson.collection(listOfNotNull(line))
}

/**
 * The finished tracks in view, drawn under the live line in a quieter colour with a halo: one
 * source, one feature per track.
 */
internal object EarlierTrackLines {
    const val SOURCE_ID = "meret-earlier-tracks"
    const val HALO_LAYER_ID = "meret-earlier-tracks-halo"
    const val LAYER_ID = "meret-earlier-tracks-line"

    /** An empty collection for no tracks; a track with nothing to draw is left out. */
    fun geoJson(lines: List<TrackLine>): String = TrackLineGeoJson.collection(lines)
}

/** Track lines as a GeoJSON `FeatureCollection`, one `MultiLineString` feature per line. */
private object TrackLineGeoJson {
    fun collection(lines: List<TrackLine>): String {
        val features =
            lines.mapNotNull { line ->
                val drawable = line.segments.filter { it.size >= 2 }
                if (drawable.isEmpty()) null else feature(drawable)
            }
        return buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(features))
        }.toString()
    }

    private fun feature(segments: List<List<LatLng>>): JsonObject = buildJsonObject {
        put("type", "Feature")
        put(
            "geometry",
            buildJsonObject {
                put("type", "MultiLineString")
                put("coordinates", JsonArray(segments.map(::lineCoordinates)))
            },
        )
        put("properties", JsonObject(emptyMap()))
    }

    private fun lineCoordinates(segment: List<LatLng>): JsonArray =
        JsonArray(segment.map(::position))

    private fun position(point: LatLng): JsonArray =
        JsonArray(listOf(JsonPrimitive(point.longitude), JsonPrimitive(point.latitude)))
}
