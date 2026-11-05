package et.alpha.meret.feature.tracking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.alpha.meret.core.model.geo.BoundingBox

/**
 * Owns the [TrackDetailViewModel]. The track id is the route argument. [onShowOnMap] gets the
 * track's id and bounds; the map lives in `:feature:map`, which this module may not see, so the
 * app opens it.
 */
@Composable
fun TrackDetailRoute(
    onBack: () -> Unit,
    onShowOnMap: (trackId: String, bounds: BoundingBox) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TrackDetailScreen(
        state = state,
        onBack = onBack,
        onShowOnMap = onShowOnMap,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
