package et.alpha.meret.core.maps.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.core.model.geo.WaypointMarker

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

    /**
     * Animates the camera to show all of [area], with room around it for the screen's own
     * controls, zoomed in no further than [maxZoom].
     */
    fun fitTo(
        area: BoundingBox,
        maxZoom: Double,
    )

    /** Draws the location puck at [position], or removes it when null. */
    fun showPuck(position: DevicePosition?)

    /** Draws the track being recorded, or removes it when null. */
    fun showTrackLine(line: TrackLine?)

    /** Draws these finished tracks under the live one, replacing the last set. */
    fun showEarlierTracks(lines: List<TrackLine>)

    /** Draws these waypoints over the tracks and under the puck, replacing the last set. */
    fun showWaypoints(markers: List<WaypointMarker>)

    /** Draws the plot being measured over the lines; empty removes it. */
    fun showMeasureRing(vertices: List<LatLng>)

    /** The ground under the middle of the map. */
    fun centre(): LatLng?
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
 *
 * [centreOn] shows a place that is not the user, such as a waypoint picked from the list, and
 * [fitTo] an area, such as a track opened from its detail screen. Both turn follow mode off,
 * so the next fix does not pull the map away again. Given before a map is attached (the map
 * tab is still opening), the latest one is kept and applied when one attaches.
 */
@Stable
public class OfflineMapController internal constructor() {
    /** The camera as last drawn, or null while no map is attached. */
    public var readout: MapCameraReadout? by mutableStateOf(null)
        private set

    /**
     * The ground on screen when the camera last came to rest, or null while no map is
     * attached. Not updated during a gesture: what to load is decided once the map stops.
     */
    public var visibleArea: BoundingBox? by mutableStateOf(null)
        private set

    /** True while the map keeps the user's position in the centre. */
    public var isFollowing: Boolean by mutableStateOf(true)
        private set

    private var target: MapCameraTarget? = null
    private var position: DevicePosition? = null
    private var trackLine: TrackLine? = null
    private var earlierTracks: List<TrackLine> = emptyList()
    private var waypoints: List<WaypointMarker> = emptyList()
    private var measureRing: List<LatLng> = emptyList()
    private var zoomInOnNextCentre = true
    private var pendingMove: CameraMove? = null

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
        pendingMove = null
        isFollowing = true
        zoomInOnNextCentre = true
        centreIfFollowing()
    }

    /**
     * Centres the map on [position], zoomed in to at least street level, and stops following
     * the user. The recentre button follows again.
     */
    public fun centreOn(position: LatLng) {
        move(CameraMove.Centre(position))
    }

    /**
     * Shows all of [area], zoomed in no further than [MAX_FIT_ZOOM] so a few metres of track
     * do not fill the screen with a blur, and stops following the user. The recentre button
     * follows again.
     */
    public fun fitTo(area: BoundingBox) {
        move(CameraMove.Fit(area))
    }

    private fun move(move: CameraMove) {
        isFollowing = false
        val target = target
        if (target == null) {
            pendingMove = move
        } else {
            pendingMove = null
            target.perform(move)
        }
    }

    private fun MapCameraTarget.perform(move: CameraMove) {
        when (move) {
            is CameraMove.Centre -> centreOn(move.position, FOLLOW_ZOOM)
            is CameraMove.Fit -> fitTo(move.area, MAX_FIT_ZOOM)
        }
    }

    /** The phone's position for the puck, or null to hide it. Follow mode tracks it. */
    public fun showPosition(position: DevicePosition?) {
        if (position == this.position) return
        this.position = position
        target?.showPuck(position)
        centreIfFollowing()
    }

    /** The track being recorded, drawn under the puck; null removes it. */
    public fun showTrackLine(line: TrackLine?) {
        if (line == trackLine) return
        trackLine = line
        target?.showTrackLine(line)
    }

    /** Finished tracks in view, drawn under the live line; empty removes them. */
    public fun showEarlierTracks(lines: List<TrackLine>) {
        if (lines == earlierTracks) return
        earlierTracks = lines
        target?.showEarlierTracks(lines)
    }

    /** Saved waypoints, clustered where they crowd; empty removes them. */
    public fun showWaypoints(markers: List<WaypointMarker>) {
        if (markers == waypoints) return
        waypoints = markers
        target?.showWaypoints(markers)
    }

    /** The plot being measured, a corner per tap; empty removes it. */
    public fun showMeasureRing(vertices: List<LatLng>) {
        if (vertices == measureRing) return
        measureRing = vertices
        target?.showMeasureRing(vertices)
    }

    /** The ground under the middle of the map, or null while no map is attached. */
    public val cameraCentre: LatLng?
        get() = target?.centre()

    internal fun attach(target: MapCameraTarget) {
        this.target = target
        target.showEarlierTracks(earlierTracks)
        target.showTrackLine(trackLine)
        target.showWaypoints(waypoints)
        target.showMeasureRing(measureRing)
        target.showPuck(position)
        val pending = pendingMove
        if (pending != null) {
            pendingMove = null
            target.perform(pending)
        } else {
            centreIfFollowing()
        }
    }

    /** Lets go of [target], unless a newer map has already attached. */
    internal fun detach(target: MapCameraTarget) {
        if (this.target === target) {
            this.target = null
            readout = null
            visibleArea = null
        }
    }

    internal fun onCameraMoved(readout: MapCameraReadout) {
        this.readout = readout
    }

    /** The camera came to rest showing [area]. */
    internal fun onCameraIdle(area: BoundingBox) {
        visibleArea = area
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

    /** A camera move asked for while no map was attached. */
    private sealed interface CameraMove {
        data class Centre(
            val position: LatLng,
        ) : CameraMove

        data class Fit(
            val area: BoundingBox,
        ) : CameraMove
    }

    private companion object {
        /** One zoom level: each tap halves or doubles the ground on screen. */
        const val ZOOM_STEP = 1.0

        /** Street level: footpaths and buildings are visible, about 2 m per dp at 9°N. */
        const val FOLLOW_ZOOM = 16.0

        /** About 0.5 m per dp: a 30 m walk still reads as a line, not a smear. */
        const val MAX_FIT_ZOOM = 18.0
    }
}

@Composable
public fun rememberOfflineMapController(): OfflineMapController = remember {
    OfflineMapController()
}
