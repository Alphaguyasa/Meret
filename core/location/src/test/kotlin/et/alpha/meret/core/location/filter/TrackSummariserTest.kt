package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.ElevationSample
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class TrackSummariserTest {
    @Test
    fun `no points is no segments and no profile`() {
        val summary = TrackSummariser.summarise(emptyList())

        assertThat(summary.segments).isEmpty()
        assertThat(summary.elevation).isEmpty()
    }

    @Test
    fun `each segment gets its own points, distance and monotonic duration`() {
        // Segment 0: 0, 10, 20 m north, a fix every 5 s. Segment 1 resumes 300 m further on
        // and walks 15 m in two fixes, 3 s apart.
        val points =
            listOf(
                fix(segment = 0, seq = 0, northMetres = 0.0, atSeconds = 0),
                fix(segment = 0, seq = 1, northMetres = 10.0, atSeconds = 5),
                fix(segment = 0, seq = 2, northMetres = 20.0, atSeconds = 10),
                fix(segment = 1, seq = 3, northMetres = 320.0, atSeconds = 100),
                fix(segment = 1, seq = 4, northMetres = 335.0, atSeconds = 103),
            )

        val segments = TrackSummariser.summarise(points).segments

        assertThat(segments.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(segments.map { it.pointCount }).containsExactly(3, 2).inOrder()
        assertThat(segments[0].distanceMetres).isWithin(TOLERANCE).of(20.0)
        // The 300 m between the pause and the resume was never observed and is not booked.
        assertThat(segments[1].distanceMetres).isWithin(TOLERANCE).of(15.0)
        assertThat(segments.map { it.durationMillis }).containsExactly(10_000L, 3_000L).inOrder()
    }

    @Test
    fun `points stored out of order are summarised in segment and sequence order`() {
        val points =
            listOf(
                fix(segment = 1, seq = 3, northMetres = 100.0, atSeconds = 50),
                fix(segment = 0, seq = 1, northMetres = 10.0, atSeconds = 5),
                fix(segment = 1, seq = 2, northMetres = 80.0, atSeconds = 40),
                fix(segment = 0, seq = 0, northMetres = 0.0, atSeconds = 0),
            )

        val segments = TrackSummariser.summarise(points).segments

        assertThat(segments.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(segments[1].distanceMetres).isWithin(TOLERANCE).of(20.0)
        assertThat(segments[1].durationMillis).isEqualTo(10_000L)
    }

    @Test
    fun `jitter under the step gate books no distance`() {
        val points =
            listOf(
                fix(seq = 0, northMetres = 0.0, atSeconds = 0),
                fix(seq = 1, northMetres = 1.5, atSeconds = 3),
                fix(seq = 2, northMetres = -1.0, atSeconds = 6),
            )

        assertThat(TrackSummariser.summarise(points).segments.single().distanceMetres)
            .isEqualTo(0.0)
    }

    @Test
    fun `an outlier counts as a point but not as distance or elevation`() {
        val points =
            listOf(
                fix(seq = 0, northMetres = 0.0, atSeconds = 0, altitude = 2_400.0),
                fix(seq = 1, northMetres = 900.0, atSeconds = 3, altitude = 9e3, outlier = true),
                fix(seq = 2, northMetres = 10.0, atSeconds = 6, altitude = 2_402.0),
            )

        val summary = TrackSummariser.summarise(points)

        assertThat(summary.segments.single().pointCount).isEqualTo(3)
        assertThat(summary.segments.single().distanceMetres).isWithin(TOLERANCE).of(10.0)
        assertThat(summary.elevation.map { it.altitudeMetres })
            .containsExactly(2_400.0, 2_402.0)
            .inOrder()
    }

    @Test
    fun `the profile follows distance along the track and skips fixes with no altitude`() {
        val points =
            listOf(
                fix(seq = 0, northMetres = 0.0, atSeconds = 0, altitude = 2_400.0),
                fix(seq = 1, northMetres = 10.0, atSeconds = 3, altitude = null),
                fix(seq = 2, northMetres = 20.0, atSeconds = 6, altitude = 2_410.0),
            )

        val elevation = TrackSummariser.summarise(points).elevation

        assertThat(elevation).hasSize(2)
        assertThat(elevation[0]).isEqualTo(ElevationSample(0.0, 2_400.0))
        assertThat(elevation[1].distanceMetres).isWithin(TOLERANCE).of(20.0)
        assertThat(elevation[1].altitudeMetres).isEqualTo(2_410.0)
    }

    @Test
    fun `a long profile is thinned to the cap and keeps both ends`() {
        val points =
            (0 until 1_000).map {
                fix(seq = it, northMetres = it * 10.0, atSeconds = it, altitude = it.toDouble())
            }

        val elevation = TrackSummariser.summarise(points, maxElevationSamples = 50).elevation

        assertThat(elevation).hasSize(50)
        assertThat(elevation.first().altitudeMetres).isEqualTo(0.0)
        assertThat(elevation.last().altitudeMetres).isEqualTo(999.0)
        assertThat(elevation.map { it.distanceMetres }).isInOrder()
    }

    @Test
    fun `a reboot inside a segment gives zero duration, never a negative one`() {
        val points =
            listOf(
                fix(seq = 0, northMetres = 0.0, atSeconds = 5_000),
                fix(seq = 1, northMetres = 10.0, atSeconds = 2),
            )

        assertThat(TrackSummariser.summarise(points).segments.single().durationMillis)
            .isEqualTo(0L)
    }

    private fun fix(
        seq: Int,
        northMetres: Double,
        atSeconds: Int,
        segment: Int = 0,
        altitude: Double? = null,
        outlier: Boolean = false,
    ): TrackPoint {
        val position =
            GeoMath.destinationPoint(
                START,
                kotlin.math.abs(northMetres),
                if (northMetres >= 0) 0.0 else 180.0,
            )
        return TrackPoint(
            id = "p$seq",
            trackId = "track",
            segmentIndex = segment,
            seq = seq,
            position = position,
            altitudeMeters = altitude,
            accuracyMeters = 4.0,
            provider = LocationProvider.GPS,
            recordedAt = atSeconds * 1_000L,
            elapsedRealtimeNanos = atSeconds * 1_000_000_000L,
            filteredPosition = position,
            isOutlier = outlier,
        )
    }

    private companion object {
        val START = LatLng(8.9806, 38.7578)
        const val TOLERANCE = 0.01
    }
}
