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
import kotlinx.coroutines.suspendCancellableCoroutine
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
 */
@Composable
public fun OfflineMap(
    packId: String,
    session: OfflineMapSession,
    modifier: Modifier = Modifier,
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

    AndroidView(factory = { mapView }, modifier = modifier)
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
