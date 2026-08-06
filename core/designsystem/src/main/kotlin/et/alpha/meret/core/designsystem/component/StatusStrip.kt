package et.alpha.meret.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.R
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * A small filled dot, optionally breathing.
 *
 * Used for recording state and sync state. It is always accompanied by text — the dot
 * alone would fail the "never rely on colour alone" rule.
 */
@Composable
public fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val alpha =
        if (pulsing) {
            rememberInfiniteTransition(label = "status-pulse")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = DOT_MIN_ALPHA,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = DOT_PULSE_MILLIS),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "status-pulse-alpha",
                ).value
        } else {
            1f
        }

    Canvas(modifier.size(Sizes.indicator)) {
        drawCircle(color = color.copy(alpha = alpha), radius = size.minDimension / 2)
    }
}

/**
 * The persistent no-network strip.
 *
 * Neutral grey, never red. Offline is the expected state for this app, not a failure,
 * and styling it as an error would undermine the entire product promise.
 */
@Composable
public fun OfflineIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        val label = stringResource(R.string.offline_working_locally)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = MeretTheme.colors.stopped)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where the outbox stands. */
public enum class SyncState { Idle, Pending, Active, Done, Conflict, Failed }

/**
 * Outbox depth at a glance. Tapping opens the sync screen.
 *
 * This never blocks anything and never raises a dialog — a failed sync is a badge, not
 * an error. The data is already safe on the device.
 */
@Composable
public fun SyncBadge(
    state: SyncState,
    pendingCount: Int,
    modifier: Modifier = Modifier,
    conflictCount: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val colors = MeretTheme.colors
    val dotColor =
        when (state) {
            SyncState.Idle, SyncState.Pending -> colors.syncPending
            SyncState.Active -> colors.syncActive
            SyncState.Done -> colors.syncDone
            SyncState.Conflict -> colors.syncConflict
            SyncState.Failed -> colors.syncFailed
        }

    val text =
        when {
            conflictCount > 0 -> stringResource(R.string.sync_conflicts, conflictCount)
            state == SyncState.Active -> stringResource(R.string.sync_uploading)
            state == SyncState.Failed -> stringResource(R.string.sync_retrying)
            pendingCount > 0 -> stringResource(R.string.sync_pending_count, pendingCount)
            else -> stringResource(R.string.sync_up_to_date)
        }

    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = Sizes.touchTargetMin)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = BADGE_SURFACE_ALPHA),
                    RoundedCornerShape(percent = BADGE_ROUNDING),
                ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .clearAndSetSemantics { contentDescription = text },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = dotColor, pulsing = state == SyncState.Active)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val DOT_PULSE_MILLIS = 900
private const val DOT_MIN_ALPHA = 0.35f
private const val BADGE_SURFACE_ALPHA = 0.92f
private const val BADGE_ROUNDING = 50

@Preview(name = "Status strip", showBackground = true, widthDp = 360)
@Composable
private fun StatusStripPreview() {
    MeretTheme {
        androidx.compose.foundation.layout.Column {
            OfflineIndicator(visible = true)
            Row(
                modifier = Modifier.padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SyncBadge(SyncState.Pending, pendingCount = 214)
                SyncBadge(SyncState.Active, pendingCount = 8)
            }
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SyncBadge(SyncState.Conflict, pendingCount = 0, conflictCount = 2)
                SyncBadge(SyncState.Done, pendingCount = 0)
            }
        }
    }
}
