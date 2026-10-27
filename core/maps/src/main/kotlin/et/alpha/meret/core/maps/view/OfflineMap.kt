package et.alpha.meret.core.maps.view

import android.content.ComponentCallbacks
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import et.alpha.meret.core.maps.OfflineMapSession
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.coroutines.resume

/**
 * A MapLibre map of the installed tile pack [packId], drawn entirely from the phone: tiles
 * through the loopback server held by [session], glyphs and sprite from assets.
 *
 * The view follows the host lifecycle, and the server is held only while the host is
 * started. No MapLibre type crosses this function, so callers never depend on MapLibre.
 *
 * With a [controller], the screen draws its own zoom buttons, compass and scale bar from it,
 * and MapLibre's compass is turned off so that only one is shown.
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
        LaunchedEffect(mapView, controller) {
            mapView.awaitMap().bindTo(controller)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Feeds [controller] this map's camera and takes its commands, until cancelled. */
private suspend fun MapLibreMap.bindTo(controller: OfflineMapController) {
    uiSettings.isCompassEnabled = false
    val target = MapLibreCamera(this)
    val onMove = MapLibreMap.OnCameraMoveListener { controller.onCameraMoved(readout()) }
    val onIdle = MapLibreMap.OnCameraIdleListener { controller.onCameraMoved(readout()) }
    addOnCameraMoveListener(onMove)
    addOnCameraIdleListener(onIdle)
    controller.attach(target)
    controller.onCameraMoved(readout())
    try {
        awaitCancellation()
    } finally {
        removeOnCameraMoveListener(onMove)
        removeOnCameraIdleListener(onIdle)
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

private class MapLibreCamera(
    private val map: MapLibreMap,
) : MapCameraTarget {
    override fun zoomBy(delta: Double) = map.animateCamera(CameraUpdateFactory.zoomBy(delta))

    override fun resetNorth() = map.resetNorth()
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
