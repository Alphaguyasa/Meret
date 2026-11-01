package et.alpha.meret.domain.waypoint

/**
 * The categories a waypoint can be saved under until projects deliver their own (form
 * templates, BUILD_PLAN phase 8c and sync, phase 9).
 *
 * [key] is what goes into `waypoint.category` and, later, to the server. It never changes
 * once a waypoint has been saved with it; the label the worker reads is a string resource.
 */
public enum class WaypointCategory(
    public val key: String,
) {
    FARM_PLOT("farm_plot"),
    HOUSEHOLD("household"),
    WATER_POINT("water_point"),
    LANDMARK("landmark"),
    OTHER("other"),
    ;

    public companion object {
        /** The category saved under [key], or null for none or a key this build does not know. */
        public fun fromKey(key: String?): WaypointCategory? = entries.firstOrNull { it.key == key }
    }
}
