package et.alpha.meret.core.maps.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Where the map's camera is, in the units the screen's own controls need. */
public data class MapCameraReadout(
    /** Degrees clockwise from north that the top of the map faces. 0 is north up. */
    val bearingDegrees: Double,
    /** Ground metres one dp spans at the centre of the map. */
    val metresPerDp: Double,
)

/** What [OfflineMapController] asks of the map. A seam; [OfflineMap] binds it to MapLibre. */
internal interface MapCameraTarget {
    fun zoomBy(delta: Double)

    fun resetNorth()
}

/**
 * Drives an [OfflineMap] from the screen's own buttons, and reports its camera for the
 * compass and scale bar, without the screen ever seeing a MapLibre type.
 *
 * Commands given before the map is ready, or after it has gone, are dropped: a zoom tap
 * that lands while the style loads should not jump the map a second later.
 */
@Stable
public class OfflineMapController internal constructor() {
    /** The camera as last drawn, or null while no map is attached. */
    public var readout: MapCameraReadout? by mutableStateOf(null)
        private set

    private var target: MapCameraTarget? = null

    public fun zoomIn() {
        target?.zoomBy(ZOOM_STEP)
    }

    public fun zoomOut() {
        target?.zoomBy(-ZOOM_STEP)
    }

    /** Turns the map back to north up. */
    public fun resetNorth() {
        target?.resetNorth()
    }

    internal fun attach(target: MapCameraTarget) {
        this.target = target
    }

    /** Lets go of [target], unless a newer map has already attached. */
    internal fun detach(target: MapCameraTarget) {
        if (this.target === target) {
            this.target = null
            readout = null
        }
    }

    internal fun onCameraMoved(readout: MapCameraReadout) {
        this.readout = readout
    }

    private companion object {
        /** One zoom level: each tap halves or doubles the ground on screen. */
        const val ZOOM_STEP = 1.0
    }
}

@Composable
public fun rememberOfflineMapController(): OfflineMapController = remember {
    OfflineMapController()
}
