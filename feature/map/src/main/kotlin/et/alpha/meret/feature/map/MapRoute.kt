package et.alpha.meret.feature.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.alpha.meret.core.maps.view.OfflineMap
import et.alpha.meret.core.maps.view.rememberOfflineMapController

/**
 * Owns the [MapViewModel] and puts the offline map under the screen's controls. The phone's
 * position goes to the map as the location puck; the recentre button turns follow mode on.
 */
@Composable
fun MapRoute(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val mapController = rememberOfflineMapController()
    LaunchedEffect(mapController, position) {
        mapController.showPosition(position)
    }

    MapScreen(
        state = state,
        onRecordTap = {},
        onRecordLongPress = {},
        onAddWaypoint = {},
        onRecentreMap = mapController::recentre,
        onOpenSync = {},
        onOpenDiagnostics = onOpenDiagnostics,
        modifier = modifier,
        contentPadding = contentPadding,
        basemap = { packId, mapModifier ->
            OfflineMap(
                packId = packId,
                session = viewModel.mapSession,
                modifier = mapModifier,
                controller = mapController,
            )
        },
        camera = mapController.readout,
        onZoomIn = mapController::zoomIn,
        onZoomOut = mapController::zoomOut,
        onResetNorth = mapController::resetNorth,
        isFollowing = mapController.isFollowing,
    )
}
