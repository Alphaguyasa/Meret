package et.alpha.meret.feature.tiles

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.component.EmptyState
import et.alpha.meret.core.designsystem.component.LayersGlyph
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * The installed map packs, and the way to add one: the list (or the empty state a fresh
 * install shows), then the import button in thumb reach. While a pack copies, the button
 * waits under a progress bar; a 400 MB pack takes minutes on a cheap phone.
 */
@Composable
fun TilePackScreen(
    state: TilePacksUiState,
    onImport: () -> Unit,
    onDelete: (packId: String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val ready = state as? TilePacksUiState.Ready ?: return
    var confirming by remember { mutableStateOf<TilePackItem?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        Box(Modifier.weight(1f)) {
            if (ready.packs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.packs_empty_title),
                    body = stringResource(R.string.packs_empty_body),
                    icon = { LayersGlyph(it, tint = MaterialTheme.colorScheme.outline) },
                )
            } else {
                PackList(packs = ready.packs, onDelete = { confirming = it })
            }
        }
        ImportBar(importing = ready.importing, onImport = onImport)
    }

    confirming?.let { pack ->
        ConfirmDelete(
            pack = pack,
            onConfirm = {
                confirming = null
                onDelete(pack.id)
            },
            onDismiss = { confirming = null },
        )
    }
    ready.message?.let { MessageDialog(it, onDismissMessage) }
}

@Composable
private fun PackList(
    packs: List<TilePackItem>,
    onDelete: (TilePackItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(packs, key = { it.id }) { pack ->
            PackRow(pack = pack, onDelete = { onDelete(pack) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun PackRow(
    pack: TilePackItem,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.xs)
                .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = pack.displayName(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = Formatter.formatShortFileSize(context, pack.bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.packs_delete)) }
    }
}

@Composable
private fun ImportBar(
    importing: Boolean,
    onImport: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (importing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.packs_importing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onImport,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.packs_import))
        }
    }
}

@Composable
private fun ConfirmDelete(
    pack: TilePackItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.packs_delete_title, pack.displayName())) },
        text = { Text(stringResource(R.string.packs_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.packs_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.packs_cancel)) }
        },
    )
}

@Composable
private fun MessageDialog(
    message: PackMessage,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message.text()) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.packs_ok)) }
        },
    )
}

@Composable
private fun PackMessage.text(): String = when (this) {
    is PackMessage.Imported -> stringResource(R.string.packs_imported, packName(name))
    is PackMessage.ImportFailed -> stringResource(problem.textRes())
    PackMessage.DeleteFailed -> stringResource(R.string.packs_delete_failed)
}

private fun ImportProblem.textRes(): Int = when (this) {
    ImportProblem.NotAPack -> R.string.packs_error_not_a_pack
    ImportProblem.OutOfSpace -> R.string.packs_error_out_of_space
    ImportProblem.ReadFailed -> R.string.packs_error_read
    ImportProblem.WriteFailed -> R.string.packs_error_write
}

@Composable
private fun TilePackItem.displayName(): String = packName(name)

/** A pack with neither a file name nor a name of its own gets a translated generic one. */
@Composable
private fun packName(name: String): String = name.ifBlank { stringResource(R.string.packs_unnamed) }

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun EmptyPreview() {
    MeretTheme {
        TilePackScreen(
            state = TilePacksUiState.Ready(packs = emptyList(), importing = false, message = null),
            onImport = {},
            onDelete = {},
            onDismissMessage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ImportingPreview() {
    MeretTheme {
        TilePackScreen(
            state =
                TilePacksUiState.Ready(
                    packs =
                        listOf(
                            TilePackItem(id = "a", name = "ethiopia-2026", bytes = 412_000_000L),
                            TilePackItem(id = "b", name = "", bytes = 38_000_000L),
                        ),
                    importing = true,
                    message = null,
                ),
            onImport = {},
            onDelete = {},
            onDismissMessage = {},
        )
    }
}
