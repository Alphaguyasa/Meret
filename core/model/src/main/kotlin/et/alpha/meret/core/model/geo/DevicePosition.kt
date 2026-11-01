package et.alpha.meret.core.model.geo

/**
 * Where the phone is, for the map's location puck. Not a track point: nothing here is
 * written anywhere.
 *
 * [isLive] is false for the platform's cached fix, which may be hours old. The map may
 * centre on it while it waits for a live fix, but draws it as stale.
 *
 * [altitudeMetres] is the fix's height above the WGS84 ellipsoid, when the fix has one. A
 * waypoint captured here keeps it.
 */
public data class DevicePosition(
    public val position: LatLng,
    public val accuracyMetres: Double,
    public val isLive: Boolean,
    public val altitudeMetres: Double? = null,
)
