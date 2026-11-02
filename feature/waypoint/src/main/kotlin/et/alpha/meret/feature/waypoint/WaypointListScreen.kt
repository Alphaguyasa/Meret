package et.alpha.meret.feature.waypoint

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.component.EmptyState
import et.alpha.meret.core.designsystem.component.PinGlyph
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.waypoint.WaypointCategory

/**
 * The saved waypoints, newest first: title (or category, or the time it was saved), category,
 * time and sync state. A tap shows the waypoint on the map. A fresh install shows the empty
 * state, which reads as "nothing yet", not as "something went wrong".
 */
@Composable
fun WaypointListScreen(
    state: WaypointListUiState,
    onOpenWaypoint: (WaypointItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val ready = state as? WaypointListUiState.Ready ?: return
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        if (ready.waypoints.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.waypoints_empty_title),
                body = stringResource(R.string.waypoints_empty_body),
                icon = { PinGlyph(it, tint = MaterialTheme.colorScheme.outline) },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(ready.waypoints, key = { it.id }) { waypoint ->
                    WaypointRow(waypoint, onOpen = { onOpenWaypoint(waypoint) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WaypointRow(
    waypoint: WaypointItem,
    onOpen: () -> Unit,
) {
    val categoryText = waypoint.categoryText()
    val timeText =
        DateUtils.formatDateTime(LocalContext.current, waypoint.recordedAtMillis, DATE_FLAGS)
    val heading = waypoint.title ?: categoryText ?: timeText
    // Whatever the heading did not already say: the category and the time, or just the time.
    val details =
        when {
            waypoint.title != null && categoryText != null -> {
                stringResource(R.string.waypoints_row_details, categoryText, timeText)
            }

            heading != timeText -> {
                timeText
            }

            else -> {
                null
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTargetField)
                .clickable(
                    onClickLabel = stringResource(R.string.waypoints_show_on_map),
                    onClick = onOpen,
                ).padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)
        if (details != null) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = syncLabel(waypoint.syncState),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaypointItem.categoryText(): String? =
    category?.let { stringResource(it.label) } ?: otherCategoryKey

@Composable
private fun syncLabel(syncState: SyncState): String = stringResource(
    when (syncState) {
        SyncState.PENDING -> R.string.waypoints_sync_pending
        SyncState.SYNCING -> R.string.waypoints_sync_syncing
        SyncState.SYNCED -> R.string.waypoints_sync_synced
        SyncState.CONFLICT -> R.string.waypoints_sync_conflict
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
        WaypointListScreen(state = WaypointListUiState.Ready(emptyList()), onOpenWaypoint = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ListPreview() {
    MeretTheme {
        WaypointListScreen(
            state =
                WaypointListUiState.Ready(
                    listOf(
                        previewWaypoint(
                            "a",
                            "Borehole",
                            WaypointCategory.WATER_POINT,
                            SyncState.PENDING,
                        ),
                        previewWaypoint("b", null, WaypointCategory.HOUSEHOLD, SyncState.SYNCED),
                        previewWaypoint("c", null, null, SyncState.PENDING),
                    ),
                ),
            onOpenWaypoint = {},
        )
    }
}

private fun previewWaypoint(
    id: String,
    title: String?,
    category: WaypointCategory?,
    syncState: SyncState,
) = WaypointItem(
    id = id,
    title = title,
    category = category,
    otherCategoryKey = null,
    recordedAtMillis = PREVIEW_RECORDED_AT,
    position = LatLng(PREVIEW_LATITUDE, PREVIEW_LONGITUDE),
    syncState = syncState,
)

private const val PREVIEW_RECORDED_AT = 1_761_800_000_000L
private const val PREVIEW_LATITUDE = 9.03
private const val PREVIEW_LONGITUDE = 38.74
