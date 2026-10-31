package et.alpha.meret.core.maps.view

import android.content.ComponentCallbacks
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.coroutines.resume
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

/**
 * A MapLibre map of the installed tile pack [packId], drawn entirely from the phone: tiles
 * through the loopback server held by [session], glyphs and sprite from assets.
 *
 * The view follows the host lifecycle, and the server is held only while the host is
 * started. No MapLibre type crosses this function, so callers never depend on MapLibre.
 *
 * With a [controller], the screen draws its own zoom buttons, compass and scale bar from it,
 * and MapLibre's compass is turned off so that only one is shown. The controller's position
 * is drawn as a puck from a GeoJSON source of our own, not MapLibre's location component,
 * which would run a second location engine beside the app's.
 */
@Composable
public fun OfflineMap(
    packId: String,
    session: OfflineMapSession,
    modifier: Modifier = Modifier,
    controller: OfflineMapController? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView =
        remember(context) {
            MapLibreSetup.ensure(context)
            MapView(context)
        }

    DisposableEffect(lifecycle, mapView) {
        val forwarder = MapLifecycleForwarder(MapViewTarget(mapView))
        val memory = LowMemoryCallback(mapView)
        lifecycle.addObserver(forwarder)
        context.registerComponentCallbacks(memory)
        onDispose {
            context.unregisterComponentCallbacks(memory)
            lifecycle.removeObserver(forwarder)
            forwarder.dispose()
        }
    }

    LaunchedEffect(lifecycle, mapView, session, packId) {
        holdStyleWhileStarted(lifecycle, session, packId) { styleUri ->
            mapView.awaitMap().setStyle(Style.Builder().fromUri(styleUri))
        }
    }

    if (controller != null) {
        val colours =
            PuckColours(
                live = LocationPuck.hex(MaterialTheme.colorScheme.primary.toArgb()),
                stale = LocationPuck.hex(MeretTheme.colors.stopped.toArgb()),
                halo = LocationPuck.hex(MeretTheme.colors.overlayHalo.toArgb()),
                track = LocationPuck.hex(MeretTheme.colors.tracking.toArgb()),
                historic = LocationPuck.hex(MeretTheme.colors.trackHistoric.toArgb()),
            )
        LaunchedEffect(mapView, controller, colours) {
            mapView.awaitMap().bindTo(mapView, controller, colours)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Feeds [controller] this map's camera and takes its commands, until cancelled. */
private suspend fun MapLibreMap.bindTo(
    view: MapView,
    controller: OfflineMapController,
    colours: PuckColours,
) {
    uiSettings.isCompassEnabled = false
    val target = MapLibreCamera(this, colours)
    val onMove = MapLibreMap.OnCameraMoveListener { controller.onCameraMoved(readout()) }
    val onIdle =
        MapLibreMap.OnCameraIdleListener {
            controller.onCameraMoved(readout())
            controller.onCameraIdle(visibleArea())
        }
    val onMoveStarted =
        MapLibreMap.OnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                controller.onUserMovedMap()
            }
        }
    // Every style load starts from the style file, without our source: add it back each time.
    val onStyle = MapView.OnDidFinishLoadingStyleListener { getStyle(target::install) }
    addOnCameraMoveListener(onMove)
    addOnCameraIdleListener(onIdle)
    addOnCameraMoveStartedListener(onMoveStarted)
    view.addOnDidFinishLoadingStyleListener(onStyle)
    getStyle(target::install)
    controller.attach(target)
    controller.onCameraMoved(readout())
    controller.onCameraIdle(visibleArea())
    try {
        awaitCancellation()
    } finally {
        removeOnCameraMoveListener(onMove)
        removeOnCameraIdleListener(onIdle)
        removeOnCameraMoveStartedListener(onMoveStarted)
        view.removeOnDidFinishLoadingStyleListener(onStyle)
        controller.detach(target)
    }
}

/**
 * MapLibre works in logical pixels (screen pixels over its pixel ratio, the display density
 * unless set otherwise), so its metres per pixel are metres per dp.
 */
private fun MapLibreMap.readout(): MapCameraReadout {
    val camera = cameraPosition
    val latitude = camera.target?.latitude ?: 0.0
    return MapCameraReadout(
        bearingDegrees = camera.bearing,
        metresPerDp = projection.getMetersPerPixelAtLatitude(latitude),
    )
}

/**
 * The smallest latitude/longitude box around the screen, turned or not. A view across the
 * antimeridian (west east of east) becomes every longitude: [BoundingBox] does not wrap, and
 * no deployment area is near 180°.
 */
private fun MapLibreMap.visibleArea(): BoundingBox {
    val bounds = projection.visibleRegion.latLngBounds
    val wraps = bounds.longitudeWest > bounds.longitudeEast
    return BoundingBox(
        minLatitude = bounds.latitudeSouth,
        minLongitude = if (wraps) -MAX_LONGITUDE else bounds.longitudeWest,
        maxLatitude = bounds.latitudeNorth,
        maxLongitude = if (wraps) MAX_LONGITUDE else bounds.longitudeEast,
    )
}

private const val MAX_LONGITUDE = 180.0

private class MapLibreCamera(
    private val map: MapLibreMap,
    private val colours: PuckColours,
) : MapCameraTarget {
    private var puckJson: String = LocationPuck.geoJson(null, colours)
    private var lineJson: String = LiveTrackLine.geoJson(null)
    private var earlierJson: String = EarlierTrackLines.geoJson(emptyList())

    override fun zoomBy(delta: Double) = map.animateCamera(CameraUpdateFactory.zoomBy(delta))

    override fun resetNorth() = map.resetNorth()

    override fun centreOn(
        position: LatLng,
        minZoom: Double?,
    ) {
        val centre = MapLibreLatLng(position.latitude, position.longitude)
        val update =
            if (minZoom != null) {
                CameraUpdateFactory.newLatLngZoom(centre, maxOf(minZoom, map.cameraPosition.zoom))
            } else {
                CameraUpdateFactory.newLatLng(centre)
            }
        map.easeCamera(update, CENTRE_ANIMATION_MS)
    }

    override fun showPuck(position: DevicePosition?) {
        puckJson = LocationPuck.geoJson(position, colours)
        // Null while a style loads; install() then draws the latest puck.
        map.style?.getSourceAs<GeoJsonSource>(LocationPuck.SOURCE_ID)?.setGeoJson(puckJson)
    }

    override fun showTrackLine(line: TrackLine?) {
        lineJson = LiveTrackLine.geoJson(line)
        map.style?.getSourceAs<GeoJsonSource>(LiveTrackLine.SOURCE_ID)?.setGeoJson(lineJson)
    }

    override fun showEarlierTracks(lines: List<TrackLine>) {
        earlierJson = EarlierTrackLines.geoJson(lines)
        map.style?.getSourceAs<GeoJsonSource>(EarlierTrackLines.SOURCE_ID)?.setGeoJson(earlierJson)
    }

    /**
     * Adds the earlier tracks, the live line over them, then the puck over both, on top of
     * [style], once per style.
     */
    fun install(style: Style) {
        installEarlierTracks(style)
        installTrackLine(style)
        installPuck(style)
    }

    private fun installEarlierTracks(style: Style) {
        val source = style.getSourceAs<GeoJsonSource>(EarlierTrackLines.SOURCE_ID)
        if (source != null) {
            source.setGeoJson(earlierJson)
            return
        }
        style.addSource(GeoJsonSource(EarlierTrackLines.SOURCE_ID, earlierJson))
        // A halo under the line: the historic blue on dark landcover is otherwise lost.
        style.addLayer(
            LineLayer(EarlierTrackLines.HALO_LAYER_ID, EarlierTrackLines.SOURCE_ID)
                .withProperties(
                    PropertyFactory.lineColor(colours.halo),
                    PropertyFactory.lineWidth(EARLIER_LINE_WIDTH_DP + 2 * EARLIER_HALO_DP),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
        style.addLayer(
            LineLayer(EarlierTrackLines.LAYER_ID, EarlierTrackLines.SOURCE_ID)
                .withProperties(
                    PropertyFactory.lineColor(colours.historic),
                    PropertyFactory.lineWidth(EARLIER_LINE_WIDTH_DP),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    private fun installTrackLine(style: Style) {
        val source = style.getSourceAs<GeoJsonSource>(LiveTrackLine.SOURCE_ID)
        if (source != null) {
            source.setGeoJson(lineJson)
            return
        }
        style.addSource(GeoJsonSource(LiveTrackLine.SOURCE_ID, lineJson))
        style.addLayer(
            LineLayer(LiveTrackLine.LAYER_ID, LiveTrackLine.SOURCE_ID)
                .withProperties(
                    PropertyFactory.lineColor(colours.track),
                    PropertyFactory.lineWidth(TRACK_LINE_WIDTH_DP),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
        )
    }

    private fun installPuck(style: Style) {
        val source = style.getSourceAs<GeoJsonSource>(LocationPuck.SOURCE_ID)
        if (source != null) {
            source.setGeoJson(puckJson)
            return
        }
        style.addSource(GeoJsonSource(LocationPuck.SOURCE_ID, puckJson))
        style.addLayer(
            FillLayer(LocationPuck.ACCURACY_LAYER_ID, LocationPuck.SOURCE_ID)
                .withFilter(kindIs(LocationPuck.KIND_ACCURACY))
                .withProperties(
                    PropertyFactory.fillColor(Expression.toColor(colourOfFeature())),
                    PropertyFactory.fillOpacity(ACCURACY_FILL_OPACITY),
                ),
        )
        style.addLayer(
            CircleLayer(LocationPuck.DOT_LAYER_ID, LocationPuck.SOURCE_ID)
                .withFilter(kindIs(LocationPuck.KIND_DOT))
                .withProperties(
                    PropertyFactory.circleRadius(DOT_RADIUS_DP),
                    PropertyFactory.circleColor(Expression.toColor(colourOfFeature())),
                    PropertyFactory.circleOpacity(Expression.get(LocationPuck.PROPERTY_OPACITY)),
                    PropertyFactory.circleStrokeWidth(DOT_HALO_DP),
                    PropertyFactory.circleStrokeColor(colours.halo),
                ),
        )
    }

    private fun kindIs(kind: String): Expression =
        Expression.eq(Expression.get(LocationPuck.PROPERTY_KIND), kind)

    private fun colourOfFeature(): Expression = Expression.get(LocationPuck.PROPERTY_COLOUR)

    private companion object {
        const val CENTRE_ANIMATION_MS = 500
        const val ACCURACY_FILL_OPACITY = 0.15f
        const val DOT_RADIUS_DP = 7f
        const val DOT_HALO_DP = 2f
        const val TRACK_LINE_WIDTH_DP = 4f
        const val EARLIER_LINE_WIDTH_DP = 3f
        const val EARLIER_HALO_DP = 1f
    }
}

private suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { map -> cont.resume(map) }
}

private class MapViewTarget(
    private val view: MapView,
) : MapLifecycleTarget {
    override fun onCreate() = view.onCreate(null)

    override fun onStart() = view.onStart()

    override fun onResume() = view.onResume()

    override fun onPause() = view.onPause()

    override fun onStop() = view.onStop()

    override fun onDestroy() = view.onDestroy()
}

private class LowMemoryCallback(
    private val view: MapView,
) : ComponentCallbacks {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = view.onLowMemory()
}
