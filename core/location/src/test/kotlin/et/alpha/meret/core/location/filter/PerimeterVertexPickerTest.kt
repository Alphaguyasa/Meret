package et.alpha.meret.core.location.filter

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

class PerimeterVertexPickerTest {
    @Test
    fun `the first usable fix becomes the first vertex`() {
        val picker = PerimeterVertexPicker()

        val vertex = picker.offer(processed(START))

        assertThat(vertex).isEqualTo(START)
        assertThat(picker.vertices).containsExactly(START)
    }

    @Test
    fun `a fix closer than the spacing to the last vertex is not a vertex`() {
        val picker = PerimeterVertexPicker(minSpacingMetres = 3.0)
        picker.offer(processed(START))

        val vertex = picker.offer(processed(north(2.0)))

        assertThat(vertex).isNull()
        assertThat(picker.vertices).containsExactly(START)
    }

    @Test
    fun `a fix at the spacing or further becomes the next vertex`() {
        val picker = PerimeterVertexPicker(minSpacingMetres = 3.0)
        picker.offer(processed(START))
        picker.offer(processed(north(2.0)))

        val vertex = picker.offer(processed(north(3.5)))

        assertThat(vertex).isEqualTo(north(3.5))
        assertThat(picker.vertices).containsExactly(START, north(3.5)).inOrder()
    }

    /** Standing still at a corner, the jitter must not pile up vertices. */
    @Test
    fun `standing still adds one vertex however many fixes arrive`() {
        val picker = PerimeterVertexPicker(minSpacingMetres = 3.0)

        repeat(STILL_FIXES) { i -> picker.offer(processed(north(if (i % 2 == 0) 0.5 else -0.5))) }

        assertThat(picker.vertices).hasSize(1)
    }

    @Test
    fun `a rejected fix is never a vertex`() {
        val picker = PerimeterVertexPicker()

        val vertex = picker.offer(processed(START, rejection = RejectionReason.IMPLIED_SPEED))

        assertThat(vertex).isNull()
        assertThat(picker.vertices).isEmpty()
    }

    @Test
    fun `a fix with no filtered position is never a vertex`() {
        val picker = PerimeterVertexPicker()

        val vertex = picker.offer(processed(START, filtered = false))

        assertThat(vertex).isNull()
    }

    @Test
    fun `a fix less accurate than the gate is skipped and the next good one counts`() {
        val picker = PerimeterVertexPicker(maxAccuracyMetres = 10.0)
        picker.offer(processed(START))

        assertThat(picker.offer(processed(north(10.0), accuracy = 15.0))).isNull()
        assertThat(picker.offer(processed(north(10.0), accuracy = 10.0))).isEqualTo(north(10.0))
        assertThat(picker.vertices).containsExactly(START, north(10.0)).inOrder()
    }

    /** The vertex is the smoothed position; the raw one stays on the track point. */
    @Test
    fun `the vertex is the filtered position, not the raw one`() {
        val picker = PerimeterVertexPicker()
        val raw = north(1.0)

        val vertex = picker.offer(processed(START, raw = raw))

        assertThat(vertex).isEqualTo(START)
    }

    @Test
    fun `a walk carries on from the vertices it was given`() {
        val picker = PerimeterVertexPicker(initialVertices = listOf(START, north(10.0)))

        assertThat(picker.offer(processed(north(11.0)))).isNull()
        assertThat(picker.offer(processed(north(14.0)))).isEqualTo(north(14.0))
        assertThat(picker.vertices).containsExactly(START, north(10.0), north(14.0)).inOrder()
    }

    @Test
    fun `the ring drops the vertices that came back to the start`() {
        val corners = listOf(START, north(20.0), northEast(20.0, 20.0), east(20.0))
        val back = listOf(east(2.0), north(1.0))
        val picker = PerimeterVertexPicker(initialVertices = corners + back, minSpacingMetres = 3.0)

        assertThat(picker.ring()).containsExactlyElementsIn(corners).inOrder()
        assertThat(picker.vertices).hasSize(corners.size + back.size)
    }

    @Test
    fun `the ring of one vertex is that vertex, and of none is empty`() {
        assertThat(PerimeterVertexPicker().ring()).isEmpty()
        val single = PerimeterVertexPicker(initialVertices = listOf(START))
        assertThat(single.ring()).containsExactly(START)
    }

    @Test
    fun `undo takes back the last vertex, and the spacing counts from the one before`() {
        val picker = PerimeterVertexPicker(initialVertices = listOf(START, north(10.0)))

        assertThat(picker.undo()).isEqualTo(north(10.0))
        assertThat(picker.offer(processed(north(4.0)))).isEqualTo(north(4.0))
        assertThat(picker.vertices).containsExactly(START, north(4.0)).inOrder()
    }

    @Test
    fun `undo on an empty walk does nothing and clear empties it`() {
        val picker = PerimeterVertexPicker()
        assertThat(picker.undo()).isNull()

        picker.offer(processed(START))
        picker.clear()

        assertThat(picker.vertices).isEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a spacing of zero is refused`() {
        PerimeterVertexPicker(minSpacingMetres = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an accuracy gate of zero is refused`() {
        PerimeterVertexPicker(maxAccuracyMetres = 0.0)
    }

    private fun processed(
        filteredPosition: LatLng,
        raw: LatLng = filteredPosition,
        accuracy: Double = 4.0,
        rejection: RejectionReason? = null,
        filtered: Boolean = rejection == null,
    ): ProcessedFix = ProcessedFix(
        point =
            TrackPoint(
                id = "p",
                trackId = "t",
                seq = 0,
                position = raw,
                accuracyMeters = accuracy,
                provider = LocationProvider.REPLAY,
                recordedAt = 0L,
                elapsedRealtimeNanos = 0L,
                filteredPosition = if (filtered) filteredPosition else null,
                isOutlier = rejection != null,
            ),
        rejectionReason = rejection,
        stepMetres = 0.0,
        totalDistanceMetres = 0.0,
        movingDurationMillis = 0L,
        motionState = MotionState.MOVING,
        startedNewSegment = false,
    )

    private companion object {
        val START = LatLng(8.9806, 38.7578)
        const val STILL_FIXES = 60

        fun north(metres: Double): LatLng = if (metres >= 0.0) {
            GeoMath.destinationPoint(START, metres, 0.0)
        } else {
            GeoMath.destinationPoint(START, -metres, 180.0)
        }

        fun east(metres: Double): LatLng = GeoMath.destinationPoint(START, metres, 90.0)

        fun northEast(
            northMetres: Double,
            eastMetres: Double,
        ): LatLng = GeoMath.destinationPoint(north(northMetres), eastMetres, 90.0)
    }
}
