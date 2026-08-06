package et.alpha.meret.core.model.geo

/**
 * A WGS84 coordinate in decimal degrees.
 *
 * Deliberately unvalidated at construction. A raw GPS fix is persisted exactly as the
 * platform reported it, nonsense included — the validity gate in the filter chain is what
 * decides whether a coordinate is usable, and it records its verdict alongside the fix
 * rather than refusing to store it. Use [isValid] where a check is actually wanted.
 */
public data class LatLng(
    public val latitude: Double,
    public val longitude: Double,
) {
    /** True when both components are real numbers inside their WGS84 ranges. */
    public val isValid: Boolean
        get() =
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
                longitude in MIN_LONGITUDE..MAX_LONGITUDE

    public companion object {
        public const val MIN_LATITUDE: Double = -90.0
        public const val MAX_LATITUDE: Double = 90.0
        public const val MIN_LONGITUDE: Double = -180.0
        public const val MAX_LONGITUDE: Double = 180.0
    }
}
