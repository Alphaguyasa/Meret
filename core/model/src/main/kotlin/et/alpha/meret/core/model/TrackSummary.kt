package et.alpha.meret.core.model

/**
 * What the track detail screen shows beyond the [Track] row: one entry per segment, and the
 * elevation profile. Derived from the raw points on read; nothing here is stored.
 */
public data class TrackSummary(
    public val segments: List<SegmentSummary>,
    /** Along the track, in order. Empty when no fix carried an altitude. */
    public val elevation: List<ElevationSample>,
)

/**
 * One stretch between a start or resume and the next pause or stop.
 *
 * [durationMillis] is measured on the monotonic clock from the segment's first fix to its
 * last, so a wrong wall clock does not change it.
 */
public data class SegmentSummary(
    public val index: Int,
    public val pointCount: Int,
    public val distanceMetres: Double,
    public val durationMillis: Long,
)

/** The altitude [altitudeMetres] at [distanceMetres] along the track. */
public data class ElevationSample(
    public val distanceMetres: Double,
    public val altitudeMetres: Double,
)

/** A track with its [summary], for the detail screen. */
public data class TrackDetail(
    public val track: Track,
    public val summary: TrackSummary,
)
