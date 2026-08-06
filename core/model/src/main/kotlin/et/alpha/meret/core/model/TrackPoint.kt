package et.alpha.meret.core.model

import et.alpha.meret.core.model.geo.LatLng

public enum class LocationProvider(
    override val wireName: String,
) : WireNamed {
    FUSED("fused"),
    GPS("gps"),
    NETWORK("network"),

    /** Emitted by `ReplayLocationEngine` from a GPX fixture. Never real field data. */
    REPLAY("replay"),
}

/**
 * A single location fix. Raw and immutable once written.
 *
 * [position] is exactly what the platform reported and is never overwritten.
 * [filteredPosition] is the Kalman output stored alongside it, so filtering can be
 * re-run later with different parameters against the original data.
 *
 * [elapsedRealtimeNanos] is the ordering key within a track, not [recordedAt]. Field
 * devices routinely have wrong wall clocks, and a clock correction mid-track would
 * otherwise reorder or duplicate points.
 *
 * [isOutlier] marks a fix the validity gate rejected. Rejected fixes are still persisted:
 * they are evidence about the device and the sky, not noise to be thrown away.
 */
public data class TrackPoint(
    public val id: String,
    public val trackId: String,
    public val segmentIndex: Int = 0,
    public val seq: Int,
    public val position: LatLng,
    public val altitudeMeters: Double? = null,
    public val accuracyMeters: Double,
    public val verticalAccuracyMeters: Double? = null,
    public val bearingDegrees: Double? = null,
    public val speedMetersPerSecond: Double? = null,
    public val speedAccuracyMetersPerSecond: Double? = null,
    public val provider: LocationProvider,
    public val satellitesUsed: Int? = null,
    public val recordedAt: Long,
    public val elapsedRealtimeNanos: Long,
    public val isMock: Boolean = false,
    public val filteredPosition: LatLng? = null,
    public val filterVariance: Double? = null,
    public val isOutlier: Boolean = false,
) {
    /** The coordinate to draw and to measure with: filtered where available, raw otherwise. */
    public val effectivePosition: LatLng
        get() = filteredPosition ?: position
}
