package et.alpha.meret.feature.waypoint

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * The waypoint sheet as a bottom sheet over whatever screen shows it (the map), owning its
 * [WaypointCaptureViewModel]. [onDismiss] takes it away: after a save, Cancel, a swipe down
 * or Back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointCaptureRoute(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WaypointCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Full height at once: the keyboard and, later, the form need the room.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                val message =
                    when (event) {
                        WaypointCaptureEvent.Saved -> {
                            R.string.waypoint_saved
                        }

                        is WaypointCaptureEvent.NotSaved -> {
                            if (event.noFix) {
                                R.string.waypoint_not_saved_no_fix
                            } else {
                                R.string.waypoint_not_saved_storage
                            }
                        }
                    }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (event == WaypointCaptureEvent.Saved) close()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        WaypointSheet(
            state = state,
            onTitleChange = viewModel::onTitleChange,
            onCategoryTap = viewModel::onCategoryTap,
            onNotesChange = viewModel::onNotesChange,
            onSave = viewModel::onSave,
            onCancel = close,
        )
    }
}
