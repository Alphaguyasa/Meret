package et.alpha.meret.feature.measure

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import et.alpha.meret.core.designsystem.format.formatArea
import et.alpha.meret.core.designsystem.format.formatDistance
import et.alpha.meret.core.designsystem.format.formatTimad
import et.alpha.meret.core.designsystem.format.labelRes
import et.alpha.meret.core.designsystem.theme.Elevations
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * Measuring a plot by its corners, as a panel over the map. The map itself stays with its
 * owner: [cameraCentre] reads the point under the cross, and [showRing] draws the ring, which
 * this route removes from the map when it leaves. [onExit] closes the panel; a ring still in
 * progress stays in its draft, and is back the next time the panel opens.
 */
@Composable
fun MeasureRoute(
    cameraCentre: () -> LatLng?,
    showRing: (List<LatLng>) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val exit by rememberUpdatedState(onExit)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                val message =
                    when (event) {
                        MeasureEvent.Saved -> R.string.measure_saved
                        MeasureEvent.NotAPlot -> R.string.measure_not_a_plot
                        MeasureEvent.StorageFailed -> R.string.measure_storage_failed
                    }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (event == MeasureEvent.Saved) exit()
            }
        }
    }
    LaunchedEffect(state.vertices) {
        showRing(state.vertices)
    }
    val clearRing by rememberUpdatedState(showRing)
    DisposableEffect(Unit) {
        onDispose { clearRing(emptyList()) }
    }
    MeasurePanel(
        state = state,
        onAddCorner = { viewModel.onAddCorner(cameraCentre()) },
        onUndo = viewModel::onUndo,
        onClear = viewModel::onClear,
        onSave = viewModel::onSave,
        onClose = onExit,
        modifier = modifier,
    )
}

@Composable
internal fun MeasurePanel(
    state: MeasureUiState,
    onAddCorner: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = Elevations.card,
        shadowElevation = Elevations.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Readout(state)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Button(
                    onClick = onAddCorner,
                    modifier = Modifier.weight(2f).heightIn(min = Sizes.touchTargetField),
                ) { Text(stringResource(R.string.measure_add_corner)) }
                OutlinedButton(
                    onClick = { naming = true },
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTargetField),
                ) { Text(stringResource(R.string.measure_save)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTargetMin),
                ) { Text(stringResource(R.string.measure_undo)) }
                TextButton(
                    onClick = { confirmingClear = true },
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTargetMin),
                ) { Text(stringResource(R.string.measure_clear)) }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).heightIn(min = Sizes.touchTargetMin),
                ) { Text(stringResource(R.string.measure_close)) }
            }
        }
    }
    if (confirmingClear) {
        ClearDialog(
            onConfirm = {
                confirmingClear = false
                onClear()
            },
            onDismiss = { confirmingClear = false },
        )
    }
    if (naming) {
        SaveDialog(
            onConfirm = { name ->
                naming = false
                onSave(name)
            },
            onDismiss = { naming = false },
        )
    }
}

/** The live figures. TalkBack reads them out as they change, a corner at a time. */
@Composable
private fun Readout(state: MeasureUiState) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (state.vertices.isEmpty()) {
            Text(
                text = stringResource(R.string.measure_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val area = formatArea(state.areaSquareMetres)
            val timad = formatTimad(state.areaSquareMetres)
            Text(
                text =
                    stringResource(
                        R.string.measure_area,
                        area.value,
                        stringResource(area.unit.labelRes),
                        timad.value,
                        stringResource(timad.unit.labelRes),
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            val perimeter = formatDistance(state.perimeterMetres)
            Text(
                text =
                    stringResource(
                        R.string.measure_perimeter,
                        perimeter.value,
                        stringResource(
                            if (perimeter.inKilometres) {
                                DesignR.string.unit_kilometres
                            } else {
                                DesignR.string.unit_metres
                            },
                        ),
                        state.vertices.size,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ClearDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measure_clear_title)) },
        text = { Text(stringResource(R.string.measure_clear_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.measure_clear)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.measure_cancel)) }
        },
    )
}

@Composable
private fun SaveDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measure_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.measure_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.measure_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.measure_cancel)) }
        },
    )
}
