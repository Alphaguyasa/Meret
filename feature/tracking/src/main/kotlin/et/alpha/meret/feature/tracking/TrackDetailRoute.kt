package et.alpha.meret.feature.tracking

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Owns the [TrackDetailViewModel]. The track id is the route argument. */
@Composable
fun TrackDetailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TrackDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TrackDetailScreen(
        state = state,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
