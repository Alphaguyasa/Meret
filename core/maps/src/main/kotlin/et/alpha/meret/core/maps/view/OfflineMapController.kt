package et.alpha.meret.core.maps.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng

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

    /**
     * Animates the camera to centre [position]. With [minZoom], zooms in to at least that
     * level on the way; without, keeps the zoom the user chose.
     */
    fun centreOn(
        position: LatLng,
        minZoom: Double?,
    )

    /** Draws the location puck at [position], or removes it when null. */
    fun showPuck(position: DevicePosition?)
}

/**
 * Drives an [OfflineMap] from the screen's own buttons, and reports its camera for the
 * compass and scale bar, without the screen ever seeing a MapLibre type.
 *
 * Zoom and compass taps given before the map is ready, or after it has gone, are dropped: a
 * zoom tap that lands while the style loads should not jump the map a second later.
 *
 * ## Follow mode
 *
 * While [isFollowing], every new position re-centres the map. It starts on, so the first
 * fix brings the map to the user, and a pan or pinch by the user turns it off. The recentre
 * button turns it back on and centres now, or at the next fix if there is none yet. The
 * first centring after it turns on zooms in to street level, so a recentre from the
 * whole-country view is useful; later ones keep the user's zoom.
 */
@Stable
public class OfflineMapController internal constructor() {
    /** The camera as last drawn, or null while no map is attached. */
    public var readout: MapCameraReadout? by mutableStateOf(null)
        private set

    /** True while the map keeps the user's position in the centre. */
    public var isFollowing: Boolean by mutableStateOf(true)
        private set

    private var target: MapCameraTarget? = null
    private var position: DevicePosition? = null
    private var zoomInOnNextCentre = true

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

    /** Centres the map on the user and keeps it there until the user moves the map. */
    public fun recentre() {
        isFollowing = true
        zoomInOnNextCentre = true
        centreIfFollowing()
    }

    /** The phone's position for the puck, or null to hide it. Follow mode tracks it. */
    public fun showPosition(position: DevicePosition?) {
        if (position == this.position) return
        this.position = position
        target?.showPuck(position)
        centreIfFollowing()
    }

    internal fun attach(target: MapCameraTarget) {
        this.target = target
        target.showPuck(position)
        centreIfFollowing()
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

    /** The user panned, pinched or turned the map: stop following. */
    internal fun onUserMovedMap() {
        isFollowing = false
    }

    private fun centreIfFollowing() {
        val target = target
        val position = position
        if (isFollowing && target != null && position != null) {
            target.centreOn(position.position, if (zoomInOnNextCentre) FOLLOW_ZOOM else null)
            zoomInOnNextCentre = false
        }
    }

    private companion object {
        /** One zoom level: each tap halves or doubles the ground on screen. */
        const val ZOOM_STEP = 1.0

        /** Street level: footpaths and buildings are visible, about 2 m per dp at 9°N. */
        const val FOLLOW_ZOOM = 16.0
    }
}

@Composable
public fun rememberOfflineMapController(): OfflineMapController = remember {
    OfflineMapController()
}
