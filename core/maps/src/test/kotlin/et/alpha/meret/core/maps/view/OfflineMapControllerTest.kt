package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import org.junit.Test

class OfflineMapControllerTest {
    private val controller = OfflineMapController()
    private val map = RecordingCamera()

    @Test
    fun `zoom buttons move the attached map one level`() {
        controller.attach(map)

        controller.zoomIn()
        controller.zoomOut()
        controller.zoomIn()

        assertThat(map.cameraCalls).containsExactly("zoom 1.0", "zoom -1.0", "zoom 1.0").inOrder()
    }

    @Test
    fun `the compass turns the map back to north`() {
        controller.attach(map)

        controller.resetNorth()

        assertThat(map.cameraCalls).containsExactly("north")
    }

    @Test
    fun `taps before the map is ready are dropped, not replayed later`() {
        controller.zoomIn()
        controller.resetNorth()

        controller.attach(map)

        assertThat(map.cameraCalls).isEmpty()
    }

    @Test
    fun `camera moves reach the readout`() {
        controller.attach(map)

        controller.onCameraMoved(MapCameraReadout(bearingDegrees = 30.0, metresPerDp = 2.4))

        assertThat(controller.readout)
            .isEqualTo(MapCameraReadout(bearingDegrees = 30.0, metresPerDp = 2.4))
    }

    @Test
    fun `a map that goes away stops taking commands and clears the readout`() {
        controller.attach(map)
        controller.onCameraMoved(MapCameraReadout(bearingDegrees = 0.0, metresPerDp = 1.0))

        controller.detach(map)
        controller.zoomIn()

        assertThat(map.cameraCalls).isEmpty()
        assertThat(controller.readout).isNull()
    }

    @Test
    fun `an old map leaving does not unhook the new one`() {
        val newer = RecordingCamera()
        controller.attach(map)
        controller.attach(newer)

        controller.detach(map)
        controller.zoomIn()

        assertThat(newer.cameraCalls).containsExactly("zoom 1.0")
        assertThat(map.cameraCalls).isEmpty()
    }

    @Test
    fun `the first fix brings the map to the user, zoomed in to street level`() {
        controller.attach(map)

        controller.showPosition(HERE)

        assertThat(map.cameraCalls).containsExactly("centre 9.03,38.74 zoom 16.0")
        assertThat(map.puck).isEqualTo(HERE)
        assertThat(controller.isFollowing).isTrue()
    }

    @Test
    fun `while following, each new fix re-centres and keeps the user's zoom`() {
        controller.attach(map)
        controller.showPosition(HERE)

        controller.showPosition(NEXT)

        assertThat(map.cameraCalls)
            .containsExactly("centre 9.03,38.74 zoom 16.0", "centre 9.031,38.74")
            .inOrder()
        assertThat(map.puck).isEqualTo(NEXT)
    }

    @Test
    fun `the same fix twice does not move the map again`() {
        controller.attach(map)
        controller.showPosition(HERE)

        controller.showPosition(HERE)

        assertThat(map.cameraCalls).hasSize(1)
    }

    @Test
    fun `a pan by the user stops following, but the puck keeps moving`() {
        controller.attach(map)
        controller.showPosition(HERE)

        controller.onUserMovedMap()
        controller.showPosition(NEXT)

        assertThat(controller.isFollowing).isFalse()
        assertThat(map.cameraCalls).containsExactly("centre 9.03,38.74 zoom 16.0")
        assertThat(map.puck).isEqualTo(NEXT)
    }

    @Test
    fun `recentre centres now, zooms in again, and follows the next fixes`() {
        controller.attach(map)
        controller.showPosition(HERE)
        controller.onUserMovedMap()

        controller.recentre()
        controller.showPosition(NEXT)

        assertThat(controller.isFollowing).isTrue()
        assertThat(map.cameraCalls)
            .containsExactly(
                "centre 9.03,38.74 zoom 16.0",
                "centre 9.03,38.74 zoom 16.0",
                "centre 9.031,38.74",
            ).inOrder()
    }

    @Test
    fun `recentre with no fix yet centres on the first one when it comes`() {
        controller.attach(map)
        controller.onUserMovedMap()

        controller.recentre()
        assertThat(map.cameraCalls).isEmpty()

        controller.showPosition(HERE)
        assertThat(map.cameraCalls).containsExactly("centre 9.03,38.74 zoom 16.0")
    }

    @Test
    fun `a map that attaches late gets the puck and the centring it missed`() {
        controller.showPosition(HERE)

        controller.attach(map)

        assertThat(map.puck).isEqualTo(HERE)
        assertThat(map.cameraCalls).containsExactly("centre 9.03,38.74 zoom 16.0")
    }

    @Test
    fun `losing the position removes the puck and leaves the camera alone`() {
        controller.attach(map)
        controller.showPosition(HERE)

        controller.showPosition(null)

        assertThat(map.puck).isNull()
        assertThat(map.cameraCalls).hasSize(1)
    }

    @Test
    fun `a track line given before the map is ready is drawn when it attaches`() {
        controller.showTrackLine(LINE)

        controller.attach(map)

        assertThat(map.lines).containsExactly(LINE)
    }

    @Test
    fun `the track line reaches the map, unchanged lines once, and null removes it`() {
        controller.attach(map)

        controller.showTrackLine(LINE)
        controller.showTrackLine(LINE)
        controller.showTrackLine(null)

        // The first null is the attach: no line yet.
        assertThat(map.lines).containsExactly(null, LINE, null).inOrder()
        assertThat(map.cameraCalls).isEmpty()
    }

    @Test
    fun `earlier tracks given before the map is ready are drawn when it attaches`() {
        controller.showEarlierTracks(listOf(LINE))

        controller.attach(map)

        assertThat(map.earlier).containsExactly(listOf(LINE))
    }

    @Test
    fun `earlier tracks reach the map once per change`() {
        controller.attach(map)

        controller.showEarlierTracks(listOf(LINE))
        controller.showEarlierTracks(listOf(LINE))
        controller.showEarlierTracks(emptyList())

        // The first empty list is the attach.
        val none = emptyList<TrackLine>()
        assertThat(map.earlier).containsExactly(none, listOf(LINE), none).inOrder()
    }

    @Test
    fun `the area on screen is known once the camera rests, and forgotten with the map`() {
        controller.attach(map)
        assertThat(controller.visibleArea).isNull()

        controller.onCameraIdle(AREA)
        assertThat(controller.visibleArea).isEqualTo(AREA)

        controller.detach(map)
        assertThat(controller.visibleArea).isNull()
    }

    private class RecordingCamera : MapCameraTarget {
        /** Camera moves only; the puck is kept apart in [puck]. */
        val cameraCalls = mutableListOf<String>()
        var puck: DevicePosition? = null

        /** Every line handed to the map, in order. */
        val lines = mutableListOf<TrackLine?>()

        /** Every set of earlier tracks handed to the map, in order. */
        val earlier = mutableListOf<List<TrackLine>>()

        override fun zoomBy(delta: Double) {
            cameraCalls += "zoom $delta"
        }

        override fun resetNorth() {
            cameraCalls += "north"
        }

        override fun centreOn(
            position: LatLng,
            minZoom: Double?,
        ) {
            val zoom = minZoom?.let { " zoom $it" }.orEmpty()
            cameraCalls += "centre ${position.latitude},${position.longitude}$zoom"
        }

        override fun showPuck(position: DevicePosition?) {
            puck = position
        }

        override fun showTrackLine(line: TrackLine?) {
            lines += line
        }

        override fun showEarlierTracks(lines: List<TrackLine>) {
            earlier += lines
        }
    }

    private companion object {
        val HERE = DevicePosition(LatLng(9.03, 38.74), accuracyMetres = 5.0, isLive = true)
        val NEXT = DevicePosition(LatLng(9.031, 38.74), accuracyMetres = 4.0, isLive = true)
        val LINE = TrackLine(listOf(listOf(LatLng(9.03, 38.74), LatLng(9.031, 38.74))))
        val AREA = BoundingBox(9.0, 38.7, 9.1, 38.8)
    }
}
