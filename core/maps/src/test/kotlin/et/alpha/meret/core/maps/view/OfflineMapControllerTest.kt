package et.alpha.meret.core.maps.view

import com.google.common.truth.Truth.assertThat
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

        assertThat(map.calls).containsExactly("zoom 1.0", "zoom -1.0", "zoom 1.0").inOrder()
    }

    @Test
    fun `the compass turns the map back to north`() {
        controller.attach(map)

        controller.resetNorth()

        assertThat(map.calls).containsExactly("north")
    }

    @Test
    fun `taps before the map is ready are dropped, not replayed later`() {
        controller.zoomIn()
        controller.resetNorth()

        controller.attach(map)

        assertThat(map.calls).isEmpty()
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

        assertThat(map.calls).isEmpty()
        assertThat(controller.readout).isNull()
    }

    @Test
    fun `an old map leaving does not unhook the new one`() {
        val newer = RecordingCamera()
        controller.attach(map)
        controller.attach(newer)

        controller.detach(map)
        controller.zoomIn()

        assertThat(newer.calls).containsExactly("zoom 1.0")
        assertThat(map.calls).isEmpty()
    }

    private class RecordingCamera : MapCameraTarget {
        val calls = mutableListOf<String>()

        override fun zoomBy(delta: Double) {
            calls += "zoom $delta"
        }

        override fun resetNorth() {
            calls += "north"
        }
    }
}
