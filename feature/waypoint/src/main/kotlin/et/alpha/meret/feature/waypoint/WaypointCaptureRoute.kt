package et.alpha.meret.feature.waypoint

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The waypoint sheet as a bottom sheet over whatever screen shows it (the map), owning its
 * [WaypointCaptureViewModel]. [onDismiss] takes it away: after a save, Cancel, a swipe down
 * or Back.
 *
 * «Take photo» hands a file in `cacheDir` to the phone's own camera app
 * ([ActivityResultContracts.TakePicture]), so Meret needs no camera permission and no camera
 * library. Which field and file the camera is writing for survive a process death while the
 * camera is open ([rememberSaveable]), and the ViewModel waits for the draft before taking the
 * photo in.
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

    var pendingField by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val key = pendingField
            val path = pendingPath
            pendingField = null
            pendingPath = null
            if (key != null && path != null) {
                if (taken) {
                    viewModel.onPhotoCaptured(key, path)
                } else {
                    scope.launch { deleteCapture(path) }
                }
            }
        }
    val onTakePhoto: (String) -> Unit = { key ->
        scope.launch {
            val capture = newCaptureFile(context)
            pendingField = key
            pendingPath = capture.path
            val launched =
                try {
                    takePicture.launch(captureUri(context, capture))
                    true
                } catch (ignored: ActivityNotFoundException) {
                    false
                } catch (ignored: SecurityException) {
                    // A camera app that refuses a caller without the camera permission.
                    false
                }
            if (!launched) {
                pendingField = null
                pendingPath = null
                deleteCapture(capture.path)
                Toast.makeText(context, R.string.form_photo_no_camera, Toast.LENGTH_LONG).show()
            }
        }
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
                            when (event.reason) {
                                NotSavedReason.NoFix -> R.string.waypoint_not_saved_no_fix
                                NotSavedReason.FormIncomplete -> R.string.waypoint_not_saved_form
                                NotSavedReason.Storage -> R.string.waypoint_not_saved_storage
                            }
                        }

                        WaypointCaptureEvent.PhotoNotSaved -> {
                            R.string.waypoint_photo_not_saved
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
            onAnswerChange = viewModel::onAnswerChange,
            onNumberTextChange = viewModel::onNumberTextChange,
            onTakePhoto = onTakePhoto,
            onRemovePhoto = viewModel::onPhotoRemove,
            onSave = viewModel::onSave,
            onCancel = close,
        )
    }
}

/** A new, empty file for the camera app to write into, in the directory the provider shares. */
private suspend fun newCaptureFile(context: Context): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
    File(directory, "${UUID.randomUUID()}.jpg")
}

private suspend fun deleteCapture(path: String) {
    withContext(Dispatchers.IO) { File(path).delete() }
}

/** A `content://` URI the camera app may write [file] through, and nothing else. */
private fun captureUri(
    context: Context,
    file: File,
) = FileProvider.getUriForFile(context, "${context.packageName}$CAPTURE_AUTHORITY_SUFFIX", file)

/** Must match `res/xml/photo_capture_paths.xml` and the provider in the manifest. */
private const val CAPTURE_DIRECTORY = "photo_captures"
private const val CAPTURE_AUTHORITY_SUFFIX = ".photocapture"
