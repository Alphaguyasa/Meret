package et.alpha.meret.core.common.geo

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.TrackLines.SegmentPoint
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import org.junit.Test

class TrackLinesTest {
    @Test
    fun `no points, no segments`() {
        assertThat(TrackLines.build(emptyList())).isEqualTo(TrackLine(emptyList()))
    }

    @Test
    fun `a pause starts a new polyline, so no line crosses the gap`() {
        val line =
            TrackLines.build(
                listOf(
                    SegmentPoint(0, north(0)),
                    SegmentPoint(0, north(50)),
                    SegmentPoint(1, north(500)),
                    SegmentPoint(1, north(550)),
                ),
            )

        assertThat(line.segments)
            .containsExactly(listOf(north(0), north(50)), listOf(north(500), north(550)))
            .inOrder()
    }

    @Test
    fun `a straight walk is drawn from its ends`() {
        val walk = (0..100).map { SegmentPoint(0, north(it * 2.0)) }

        assertThat(TrackLines.build(walk).segments).containsExactly(listOf(north(0), north(200)))
    }

    @Test
    fun `a turn is kept`() {
        val corner = LatLng(BASE.latitude + 100 * DEGREES_PER_METRE, BASE.longitude)
        val east = LatLng(corner.latitude, corner.longitude + 100 * DEGREES_PER_METRE)
        val line =
            TrackLines.build(
                listOf(SegmentPoint(0, BASE), SegmentPoint(0, corner), SegmentPoint(0, east)),
            )

        assertThat(line.segments.single()).containsExactly(BASE, corner, east).inOrder()
    }

    @Test
    fun `a segment of one fix is kept`() {
        assertThat(TrackLines.build(listOf(SegmentPoint(3, BASE))).segments)
            .containsExactly(listOf(BASE))
    }

    /** [metres] north of [BASE]. */
    private fun north(metres: Number) =
        LatLng(BASE.latitude + metres.toDouble() * DEGREES_PER_METRE, BASE.longitude)

    private companion object {
        val BASE = LatLng(9.03, 38.74)
        const val DEGREES_PER_METRE = 1.0 / 111_195.0
    }
}
