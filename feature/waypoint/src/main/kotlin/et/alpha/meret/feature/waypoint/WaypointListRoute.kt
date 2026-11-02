package et.alpha.meret.feature.waypoint

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.alpha.meret.core.model.geo.LatLng

/**
 * Owns the [WaypointListViewModel]. A tap on a row hands its position to [onShowOnMap]: the
 * map lives in `:feature:map`, which this module may not see, so the app does the rest.
 */
@Composable
fun WaypointListRoute(
    onShowOnMap: (position: LatLng) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: WaypointListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WaypointListScreen(
        state = state,
        onOpenWaypoint = { onShowOnMap(it.position) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
