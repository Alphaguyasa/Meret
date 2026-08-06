package et.alpha.meret.core.model

public enum class MeasurementKind(
    override val wireName: String,
) : WireNamed {
    AREA("area"),
    DISTANCE("distance"),
}

public enum class MeasurementMethod(
    override val wireName: String,
) : WireNamed {
    /** Vertices sampled from filtered fixes while walking the perimeter. */
    WALKED("walked"),

    /** Vertices placed by tapping the map. */
    TAPPED("tapped"),
}

/**
 * A saved area or distance measurement.
 *
 * [areaSquareMeters] and [perimeterMeters] are populated for [MeasurementKind.AREA];
 * [distanceMeters] for [MeasurementKind.DISTANCE]. Area is geodesic, computed by spherical
 * excess, not by projecting to a plane — a one-hectare plot measured on a local planar
 * approximation is fine, a 200-hectare one is not.
 */
public data class Measurement(
    public val id: String,
    public val projectId: String,
    public val name: String,
    public val kind: MeasurementKind,
    public val verticesGeoJson: String,
    public val areaSquareMeters: Double? = null,
    public val perimeterMeters: Double? = null,
    public val distanceMeters: Double? = null,
    public val method: MeasurementMethod,
    public val recordedAt: Long,
    public val updatedAt: Long,
    public val syncState: SyncState = SyncState.PENDING,
)
