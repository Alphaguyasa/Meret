package et.alpha.meret.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.R
import et.alpha.meret.core.designsystem.theme.Elevations
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing

/** Everything the tracking bar needs to render. Pre-formatted — the bar does no maths. */
public data class TrackingStats(
    val state: RecordingState,
    val duration: String,
    val distance: String,
    val distanceUnit: String,
    val pointCount: String,
    val segmentCount: String,
    val accuracy: String,
)

/**
 * Bottom bar on the map screen.
 *
 * Collapsed it shows the three things that matter while walking: state, duration,
 * distance. Expanded it adds the diagnostics you check when something looks wrong.
 *
 * Sits in the bottom third of the screen because that is where a thumb reaches on a
 * 6.7" phone held one-handed. The content animates its own height rather than being
 * given a fixed one, so it reflows instead of clipping at a 200% font scale.
 */
@Composable
public fun TrackingBar(
    stats: TrackingStats,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MeretTheme.colors
    val statusColor =
        when (stats.state) {
            RecordingState.Idle -> colors.stopped
            RecordingState.Recording -> colors.tracking
            RecordingState.Paused -> colors.paused
        }
    val statusLabel =
        stringResource(
            when (stats.state) {
                RecordingState.Idle -> R.string.tracking_state_stopped
                RecordingState.Recording -> R.string.tracking_state_recording
                RecordingState.Paused -> R.string.tracking_state_paused
            },
        )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevations.sheet,
        shadowElevation = Elevations.sheet,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .animateContentSize()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Screen-reader users should hear the state change without having to
                        // go looking for it.
                        .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    StatusDot(
                        color = statusColor,
                        pulsing = stats.state == RecordingState.Recording,
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Metric(
                    value = stats.duration,
                    unit = "",
                    label = stringResource(R.string.metric_duration),
                    compact = true,
                )
                Metric(
                    value = stats.distance,
                    unit = stats.distanceUnit,
                    label = stringResource(R.string.metric_distance),
                    compact = true,
                    modifier = Modifier.weight(1f),
                )

                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "chevron",
                )
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(Sizes.touchTargetMin),
                ) {
                    Chevron(
                        modifier =
                            Modifier
                                .size(Sizes.icon)
                                .rotate(chevronRotation),
                        contentDescription =
                            stringResource(
                                if (expanded) R.string.action_collapse else R.string.action_expand,
                            ),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.sm),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Metric(
                            value = stats.pointCount,
                            unit = "",
                            label = stringResource(R.string.metric_points),
                            compact = true,
                        )
                        Metric(
                            value = stats.segmentCount,
                            unit = "",
                            label = stringResource(R.string.metric_segments),
                            compact = true,
                        )
                        Metric(
                            value = stats.accuracy,
                            unit = stringResource(R.string.unit_metres),
                            label = stringResource(R.string.metric_accuracy),
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Tracking bar", showBackground = true, widthDp = 380)
@Composable
private fun TrackingBarPreview() {
    val stats =
        TrackingStats(
            state = RecordingState.Recording,
            duration = "01:42:07",
            distance = "4.82",
            distanceUnit = "km",
            pointCount = "1,204",
            segmentCount = "3",
            accuracy = "4",
        )
    MeretTheme {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TrackingBar(stats, expanded = false, onToggleExpanded = {})
            TrackingBar(stats, expanded = true, onToggleExpanded = {})
        }
    }
}
