package et.alpha.meret.feature.tracking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Owns the [TrackListViewModel]. */
@Composable
fun TrackListRoute(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TrackListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TrackListScreen(
        state = state,
        onDelete = viewModel::deleteTrack,
        onUndoDelete = viewModel::undoDelete,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
