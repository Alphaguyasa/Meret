package et.alpha.meret.feature.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.alpha.meret.core.maps.view.OfflineMap

/** Owns the [MapViewModel] and puts the offline map under the screen's controls. */
@Composable
fun MapRoute(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    MapScreen(
        state = state,
        onRecordTap = {},
        onRecordLongPress = {},
        onAddWaypoint = {},
        onRecentreMap = {},
        onOpenSync = {},
        onOpenDiagnostics = onOpenDiagnostics,
        modifier = modifier,
        contentPadding = contentPadding,
        basemap = { packId, mapModifier ->
            OfflineMap(packId = packId, session = viewModel.mapSession, modifier = mapModifier)
        },
    )
}
