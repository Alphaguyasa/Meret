package et.alpha.meret.core.location.filter

import et.alpha.meret.core.model.ElevationSample
import et.alpha.meret.core.model.SegmentSummary
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.TrackSummary
import et.alpha.meret.core.model.TrackingConfig

/**
 * Segment totals and the elevation profile of a recorded track, from its stored points.
 *
 * Distance is re-run through [DistanceAccumulator] on the filtered positions, segment by
 * segment, so each segment's figure obeys the same step gate as the track's running total.
 * Outliers count as points (they are stored) but never as distance or elevation. Raw points
 * are read, never changed.
 *
 * The elevation profile is at most [maxElevationSamples] long: a phone screen cannot draw
 * more, and a 20,000-point track would otherwise build a path of 20,000 vertices.
 */
public object TrackSummariser {
    public const val DEFAULT_MAX_ELEVATION_SAMPLES: Int = 200

    public fun summarise(
        points: List<TrackPoint>,
        config: TrackingConfig = TrackingConfig(),
        maxElevationSamples: Int = DEFAULT_MAX_ELEVATION_SAMPLES,
    ): TrackSummary {
        require(maxElevationSamples >= 2) { "maxElevationSamples must be at least 2" }
        val accumulator = DistanceAccumulator(config)
        val elevation = mutableListOf<ElevationSample>()
        val segments =
            points
                .sortedWith(compareBy({ it.segmentIndex }, { it.seq }))
                .groupBy { it.segmentIndex }
                .map { (index, segment) ->
                    accumulator.startNewSegment()
                    val startMetres = accumulator.totalMetres
                    for (point in segment.filterNot { it.isOutlier }) {
                        accumulator.accumulate(point.effectivePosition, point.accuracyMeters)
                        point.altitudeMeters?.let {
                            elevation += ElevationSample(accumulator.totalMetres, it)
                        }
                    }
                    SegmentSummary(
                        index = index,
                        pointCount = segment.size,
                        distanceMetres = accumulator.totalMetres - startMetres,
                        durationMillis = segment.durationMillis(),
                    )
                }
        return TrackSummary(segments, elevation.thinnedTo(maxElevationSamples))
    }

    /** First to last fix on the monotonic clock; 0 for one fix, or a reboot mid-segment. */
    private fun List<TrackPoint>.durationMillis(): Long {
        val nanos = last().elapsedRealtimeNanos - first().elapsedRealtimeNanos
        return (nanos / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    /** Evenly spaced picks that always keep the first and last sample. */
    private fun List<ElevationSample>.thinnedTo(max: Int): List<ElevationSample> {
        if (size <= max) return this
        val step = (size - 1).toDouble() / (max - 1)
        return List(max) { i -> if (i == max - 1) last() else this[(i * step).toInt()] }
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
