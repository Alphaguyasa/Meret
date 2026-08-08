package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.location.FixedTimeProvider
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class FixPipelineTest {
    @Test
    fun `an accepted fix carries both raw and filtered positions`() {
        val pipeline = pipeline()

        val processed = pipeline.process(fix(nanos = seconds(1)))

        assertThat(processed.wasAccepted).isTrue()
        assertThat(processed.point.position).isEqualTo(START)
        assertThat(processed.point.filteredPosition).isNotNull()
        assertThat(processed.point.isOutlier).isFalse()
    }

    /**
     * Invariant 2 in `CLAUDE.md`: filtering never deletes a raw fix. A rejected fix is a row in
     * the track like any other — it is evidence about the device and the sky.
     */
    @Test
    fun `a rejected fix is still emitted as a point`() {
        val pipeline = pipeline()

        val processed = pipeline.process(fix(accuracy = 90.0, nanos = seconds(1)))

        assertThat(processed.wasAccepted).isFalse()
        assertThat(processed.rejectionReason).isEqualTo(RejectionReason.ACCURACY)
        assertThat(processed.point.isOutlier).isTrue()
        assertThat(processed.point.position).isEqualTo(START)
    }

    /**
     * A rejected fix was never fed to the filter, so there is no smoothed value to report.
     * Storing the raw position in `filtered_lat/lon` would make an outlier indistinguishable
     * from a good fix to everything downstream that draws from the filtered columns.
     */
    @Test
    fun `a rejected fix has no filtered position`() {
        val pipeline = pipeline()

        val processed = pipeline.process(fix(accuracy = 90.0, nanos = seconds(1)))

        assertThat(processed.point.filteredPosition).isNull()
        assertThat(processed.point.filterVariance).isNull()
    }

    @Test
    fun `seq advances for rejected fixes too`() {
        val pipeline = pipeline()

        val first = pipeline.process(fix(nanos = seconds(1)))
        val rejected = pipeline.process(fix(accuracy = 90.0, nanos = seconds(2)))
        val third = pipeline.process(fix(nanos = seconds(3)))

        // seq numbers rows in the track, and the outlier is a row. Reusing 1 would collide with
        // the unique index on (track_id, seq) and the insert would be silently ignored.
        assertThat(listOf(first.point.seq, rejected.point.seq, third.point.seq))
            .containsExactly(0, 1, 2)
            .inOrder()
    }

    @Test
    fun `every point gets a distinct id`() {
        val pipeline = pipeline()

        val ids = (1..50).map { pipeline.process(fix(nanos = seconds(it.toLong()))).point.id }

        assertThat(ids.toSet()).hasSize(50)
    }

    @Test
    fun `points are stamped with the track id`() {
        val pipeline = pipeline(trackId = "a-specific-track")

        val processed = pipeline.process(fix(nanos = seconds(1)))

        assertThat(processed.point.trackId).isEqualTo("a-specific-track")
    }

    /**
     * The single most important property of the chain. A 200 m outlier that reached the filter
     * would drag the smoothed line toward itself for several seconds and book a 400 m round
     * trip of distance.
     */
    @Test
    fun `a rejected fix does not disturb the smoothed line or the distance`() {
        val clean = pipeline()
        val disturbed = pipeline()

        val walk = (1..20).map { fix(position = north(it * 5.0), nanos = seconds(it.toLong())) }
        val cleanResults = walk.map { clean.process(it) }

        val withOutlier =
            walk.take(10) +
                fix(position = north(200.0), nanos = seconds(10) + 500_000_000L) +
                walk.drop(10)
        val disturbedResults = withOutlier.map { disturbed.process(it) }

        assertThat(disturbedResults.count { !it.wasAccepted }).isEqualTo(1)
        assertThat(disturbed.totalDistanceMetres)
            .isWithin(0.001)
            .of(clean.totalDistanceMetres)
        assertThat(disturbedResults.last().point.filteredPosition)
            .isEqualTo(cleanResults.last().point.filteredPosition)
    }

    @Test
    fun `moving time accrues between accepted fixes while moving`() {
        val pipeline = pipeline()

        (1..10).forEach {
            pipeline.process(
                fix(position = north(it * 5.0), nanos = seconds(it.toLong())),
            )
        }

        // Nine one-second intervals between ten fixes.
        assertThat(pipeline.totalMovingDurationMillis).isEqualTo(9_000L)
    }

    @Test
    fun `moving time does not accrue across a segment boundary`() {
        val pipeline = pipeline()
        pipeline.process(fix(nanos = seconds(1)))

        pipeline.process(fix(position = north(420.0), nanos = seconds(1) + seconds(300)))

        // Nobody observed what happened during the gap, so none of those five minutes is
        // moving time.
        assertThat(pipeline.totalMovingDurationMillis).isEqualTo(0L)
    }

    @Test
    fun `a gap opens a new segment and books no distance across it`() {
        val pipeline = pipeline()
        pipeline.process(fix(nanos = seconds(1)))

        val afterGap =
            pipeline.process(fix(position = north(420.0), nanos = seconds(1) + seconds(300)))

        assertThat(afterGap.startedNewSegment).isTrue()
        assertThat(afterGap.point.segmentIndex).isEqualTo(1)
        assertThat(afterGap.stepMetres).isEqualTo(0.0)
        assertThat(pipeline.totalDistanceMetres).isEqualTo(0.0)
    }

    @Test
    fun `an explicit new segment breaks the line without losing the totals`() {
        val pipeline = pipeline()
        (1..20).forEach {
            pipeline.process(
                fix(position = north(it * 5.0), nanos = seconds(it.toLong())),
            )
        }
        val distanceBefore = pipeline.totalDistanceMetres

        pipeline.startNewSegment()
        val resumed = pipeline.process(fix(position = north(500.0), nanos = seconds(21)))

        assertThat(resumed.startedNewSegment).isTrue()
        assertThat(resumed.stepMetres).isEqualTo(0.0)
        assertThat(pipeline.totalDistanceMetres).isEqualTo(distanceBefore)
    }

    @Test
    fun `running totals are absolute rather than deltas`() {
        val pipeline = pipeline()

        val results =
            (1..20).map {
                pipeline.process(
                    fix(position = north(it * 5.0), nanos = seconds(it.toLong())),
                )
            }

        // A caller writing a batch takes the last element and stores it on the track without
        // summing anything.
        assertThat(results.last().totalDistanceMetres).isEqualTo(pipeline.totalDistanceMetres)
        assertThat(results.map { it.totalDistanceMetres }).isInOrder()
    }

    @Test
    fun `point count tracks every row emitted`() {
        val pipeline = pipeline()

        pipeline.process(fix(nanos = seconds(1)))
        pipeline.process(fix(accuracy = 90.0, nanos = seconds(2)))

        assertThat(pipeline.pointCount).isEqualTo(2)
    }

    /**
     * A restarted service must not begin at `seq = 0`: `(track_id, seq)` is unique, so the whole
     * batch would collide with the rows already written and be silently ignored.
     */
    @Test
    fun `resuming continues the sequence and the totals`() {
        val pipeline = pipeline()

        pipeline.resumeFrom(
            nextSeq = 500,
            distanceMetres = 1_234.5,
            movingMillis = 60_000L,
            segmentIndex = 3,
        )
        val first = pipeline.process(fix(nanos = seconds(1)))

        assertThat(first.point.seq).isEqualTo(500)
        assertThat(pipeline.totalDistanceMetres).isEqualTo(1_234.5)
        assertThat(pipeline.totalMovingDurationMillis).isEqualTo(60_000L)
        // The filter state died with the process, so the line has to break here.
        assertThat(first.startedNewSegment).isTrue()
        assertThat(first.point.segmentIndex).isEqualTo(4)
    }

    @Test
    fun `resuming rejects a negative sequence`() {
        val pipeline = pipeline()

        val failure =
            runCatching { pipeline.resumeFrom(-1, 0.0, 0L, 0) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `raw fix metadata survives into the point`() {
        val pipeline = pipeline()

        val processed =
            pipeline.process(
                RawFix(
                    position = START,
                    accuracyMetres = 4.0,
                    elapsedRealtimeNanos = seconds(1),
                    recordedAtMillis = 1_773_000_000_123L,
                    provider = LocationProvider.FUSED,
                    altitudeMetres = 2_355.0,
                    verticalAccuracyMetres = 3.5,
                    bearingDegrees = 87.5,
                    speedMetresPerSecond = 1.4,
                    speedAccuracyMetresPerSecond = 0.3,
                    satellitesUsed = 11,
                    isMock = false,
                ),
            )

        val point = processed.point
        assertThat(point.altitudeMeters).isEqualTo(2_355.0)
        assertThat(point.verticalAccuracyMeters).isEqualTo(3.5)
        assertThat(point.bearingDegrees).isEqualTo(87.5)
        assertThat(point.speedMetersPerSecond).isEqualTo(1.4)
        assertThat(point.speedAccuracyMetersPerSecond).isEqualTo(0.3)
        assertThat(point.satellitesUsed).isEqualTo(11)
        assertThat(point.provider).isEqualTo(LocationProvider.FUSED)
        assertThat(point.recordedAt).isEqualTo(1_773_000_000_123L)
        assertThat(point.elapsedRealtimeNanos).isEqualTo(seconds(1))
    }

    private fun pipeline(
        config: TrackingConfig = TrackingConfig(),
        trackId: String = "track-under-test",
    ) = FixPipeline(
        trackId = trackId,
        config = config,
        uuidGenerator = UuidV7Generator(FixedTimeProvider()),
    )

    private fun fix(
        position: LatLng = START,
        accuracy: Double = 4.0,
        nanos: Long = seconds(1),
    ) = RawFix(
        position = position,
        accuracyMetres = accuracy,
        elapsedRealtimeNanos = nanos,
        recordedAtMillis = 1_773_000_000_000L,
        provider = LocationProvider.GPS,
    )

    private fun north(metres: Double): LatLng = GeoMath.destinationPoint(START, metres, 0.0)

    private fun seconds(value: Long) = value * 1_000_000_000L

    private companion object {
        val START = LatLng(8.9806, 38.7578)
    }
}
