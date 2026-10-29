package et.alpha.meret.feature.tiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Owns the [TilePacksViewModel] and the document picker. The picker asks for any file type:
 * many file managers give `.mbtiles` no MIME type, and the import checks the file itself.
 */
@Composable
fun TilePacksRoute(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: TilePacksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.importPack(it.toString()) }
        }

    TilePackScreen(
        state = state,
        onImport = { picker.launch(arrayOf(ANY_FILE)) },
        onDelete = viewModel::deletePack,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

private const val ANY_FILE = "*/*"
