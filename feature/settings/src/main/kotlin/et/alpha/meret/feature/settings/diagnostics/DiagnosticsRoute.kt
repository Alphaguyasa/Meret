package et.alpha.meret.feature.settings.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import et.alpha.meret.feature.settings.R

/**
 * Owns the [DiagnosticsViewModel] and the clipboard. [onRecheckPermissions] shows the
 * permission onboarding flow; it is called once the skips are cleared.
 */
@Composable
fun DiagnosticsRoute(
    onRecheckPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.openOnboarding.collect { onRecheckPermissions() }
        }
    }

    DiagnosticsScreen(
        state = state,
        onCopy = { report -> context.copyReport(report) },
        onRecheckPermissions = viewModel::recheckPermissions,
        modifier = modifier,
    )
}

/**
 * Puts the report on the clipboard. Android 13+ confirms a copy itself; below that a toast
 * says it worked and what to do next.
 */
private fun Context.copyReport(report: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_title), report))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_LONG).show()
    }
}
