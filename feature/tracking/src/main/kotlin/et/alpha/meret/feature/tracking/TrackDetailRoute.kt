package et.alpha.meret.feature.tracking

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
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
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    TrackDetailEvent.RenameFailed -> {
                        val message = R.string.track_rename_failed
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    TrackDetailScreen(
        state = state,
        onBack = onBack,
        onShowOnMap = onShowOnMap,
        onRename = viewModel::onRename,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
