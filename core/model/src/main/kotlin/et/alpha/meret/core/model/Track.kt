package et.alpha.meret.core.model

import et.alpha.meret.core.model.geo.BoundingBox

public enum class TrackStatus(
    override val wireName: String,
) : WireNamed {
    /** Recording. At most one track per device is in this state. */
    ACTIVE("active"),

    /** Recording suspended, by the user or by auto-pause. Moving time stops accruing. */
    PAUSED("paused"),

    COMPLETED("completed"),

    /** Ended and abandoned. Points are retained until the row is purged. */
    DISCARDED("discarded"),
}

public enum class TrackPurpose(
    override val wireName: String,
) : WireNamed {
    SURVEY("survey"),
    TRANSIT("transit"),
    MEASUREMENT("measurement"),
}

/**
 * One recording session.
 *
 * [pointCount], [distanceMeters] and [movingDurationMillis] are denormalised running
 * totals maintained on every batch write, so the track list never has to aggregate over
 * `track_point`. [distanceMeters] is measured on filtered geometry with the step gate
 * applied; it is not the sum of raw fix-to-fix distances.
 *
 * [bounds] is null until the first accepted fix. [simplifiedGeoJson] is Douglas–Peucker
 * output, regenerated on completion and never on the fix path.
 */
public data class Track(
    public val id: String,
    public val projectId: String,
    public val deviceId: String,
    public val userId: String,
    public val name: String? = null,
    public val startedAt: Long,
    public val endedAt: Long? = null,
    public val status: TrackStatus,
    public val pointCount: Int = 0,
    public val distanceMeters: Double = 0.0,
    public val movingDurationMillis: Long = 0L,
    public val bounds: BoundingBox? = null,
    public val simplifiedGeoJson: String? = null,
    public val purpose: TrackPurpose? = null,
    public val notes: String? = null,
    public val syncState: SyncState = SyncState.PENDING,
    public val updatedAt: Long,
) {
    public val isRecording: Boolean
        get() = status == TrackStatus.ACTIVE || status == TrackStatus.PAUSED
}
