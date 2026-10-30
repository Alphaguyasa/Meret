package et.alpha.meret.feature.tracking

import android.text.format.DateUtils
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.component.EmptyState
import et.alpha.meret.core.designsystem.component.TrackGlyph
import et.alpha.meret.core.designsystem.format.formatDistance
import et.alpha.meret.core.designsystem.format.formatDuration
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * The recorded tracks, newest first: name or start date, distance, moving time, and where
 * the track stands (recording, not finished, or its sync state). A fresh install shows the
 * empty state, which reads as "nothing yet", not as "something went wrong".
 *
 * Delete has no confirmation dialog: the row goes at once and the snackbar offers Undo for
 * as long as the view model's undo window is open.
 */
@Composable
fun TrackListScreen(
    state: TrackListUiState,
    onDelete: (trackId: String) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val ready = state as? TrackListUiState.Ready ?: return
    val snackbarHost = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.tracks_deleted)
    val undoText = stringResource(R.string.tracks_undo)
    val undo by rememberUpdatedState(onUndoDelete)

    // Keyed on the pending track: when the window closes the key changes, and cancelling
    // showSnackbar takes the snackbar down.
    LaunchedEffect(ready.pendingDelete?.id) {
        if (ready.pendingDelete == null) return@LaunchedEffect
        val result =
            snackbarHost.showSnackbar(
                message = deletedText,
                actionLabel = undoText,
                duration = SnackbarDuration.Indefinite,
            )
        if (result == SnackbarResult.ActionPerformed) undo()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        if (ready.tracks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.tracks_empty_title),
                body = stringResource(R.string.tracks_empty_body),
                icon = { TrackGlyph(it, tint = MaterialTheme.colorScheme.outline) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(ready.tracks, key = { it.id }) { track ->
                    TrackRow(track = track, onDelete = { onDelete(track.id) })
                    HorizontalDivider()
                }
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
    }

    ready.message?.let { MessageDialog(it, onDismissMessage) }
}

@Composable
private fun TrackRow(
    track: TrackItem,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.xs)
                .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = track.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = track.stats(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val (label, color) = track.standing()
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }
        if (track.canDelete) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.tracks_delete)) }
        }
    }
}

@Composable
private fun MessageDialog(
    message: TrackListMessage,
    onDismiss: () -> Unit,
) {
    val text =
        when (message) {
            TrackListMessage.DeleteRefusedRecording -> R.string.tracks_delete_refused_recording
            TrackListMessage.DeleteFailed -> R.string.tracks_delete_failed
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracks_ok)) }
        },
    )
}

/** The track's own name, or when it started, in the phone's language and date order. */
@Composable
private fun TrackItem.title(): String {
    if (!name.isNullOrBlank()) return name
    val context = LocalContext.current
    return DateUtils.formatDateTime(context, startedAtMillis, DATE_FLAGS)
}

@Composable
private fun TrackItem.stats(): String {
    val distance = formatDistance(distanceMetres)
    val unit =
        if (distance.inKilometres) {
            DesignR.string.unit_kilometres
        } else {
            DesignR.string.unit_metres
        }
    return stringResource(
        R.string.tracks_row_stats,
        distance.value,
        stringResource(unit),
        formatDuration(movingDurationMillis),
    )
}

/** Recording state first; a finished track shows where it stands with the server. */
@Composable
private fun TrackItem.standing(): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    return when (progress) {
        TrackProgress.Recording -> stringResource(R.string.tracks_recording) to colors.primary
        TrackProgress.Paused -> stringResource(R.string.tracks_paused) to colors.primary
        TrackProgress.NotFinished -> stringResource(R.string.tracks_not_finished) to colors.error
        TrackProgress.Finished -> syncLabel(syncState) to colors.onSurfaceVariant
    }
}

@Composable
private fun syncLabel(syncState: SyncState): String = stringResource(
    when (syncState) {
        SyncState.PENDING -> R.string.tracks_sync_pending
        SyncState.SYNCING -> R.string.tracks_sync_syncing
        SyncState.SYNCED -> R.string.tracks_sync_synced
        SyncState.CONFLICT -> R.string.tracks_sync_conflict
    },
)

private const val DATE_FLAGS =
    DateUtils.FORMAT_SHOW_DATE or
        DateUtils.FORMAT_SHOW_YEAR or
        DateUtils.FORMAT_ABBREV_MONTH or
        DateUtils.FORMAT_SHOW_TIME

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun EmptyPreview() {
    MeretTheme {
        TrackListScreen(
            state =
                TrackListUiState.Ready(tracks = emptyList(), pendingDelete = null, message = null),
            onDelete = {},
            onUndoDelete = {},
            onDismissMessage = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ListPreview() {
    MeretTheme {
        TrackListScreen(
            state =
                TrackListUiState.Ready(
                    tracks =
                        listOf(
                            previewTrack("a", TrackProgress.Recording, SyncState.PENDING),
                            previewTrack("b", TrackProgress.NotFinished, SyncState.PENDING),
                            previewTrack("c", TrackProgress.Finished, SyncState.SYNCED),
                        ),
                    pendingDelete = null,
                    message = null,
                ),
            onDelete = {},
            onUndoDelete = {},
            onDismissMessage = {},
        )
    }
}

private fun previewTrack(
    id: String,
    progress: TrackProgress,
    syncState: SyncState,
) = TrackItem(
    id = id,
    name = null,
    startedAtMillis = PREVIEW_STARTED_AT,
    distanceMetres = PREVIEW_DISTANCE_METRES,
    movingDurationMillis = PREVIEW_MOVING_MS,
    progress = progress,
    syncState = syncState,
)

private const val PREVIEW_STARTED_AT = 1_761_800_000_000L
private const val PREVIEW_DISTANCE_METRES = 2_460.0
private const val PREVIEW_MOVING_MS = 2_520_000L
