package et.alpha.meret.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * A labelled number.
 *
 * "Show the number" is a design rule, not a preference — field workers do not trust
 * progress bars. Values render in tabular figures so a ticking duration does not shift
 * the layout once a second for an entire working day.
 */
@Composable
public fun Metric(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    compact: Boolean = false,
    /** Spoken form, e.g. "4.8 kilometres". The visual form abbreviates; speech should not. */
    accessibilityText: String = "$value $unit $label",
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = accessibilityText },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style =
                    if (compact) {
                        MeretTheme.typography.metricSmall
                    } else {
                        MeretTheme.typography.metricLarge
                    },
                color = valueColor,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MeretTheme.typography.metricUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = Spacing.xxs,
                            bottom = if (compact) 1.dpAsBaselineNudge else 3.dpAsBaselineNudge,
                        ),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * Reads better at the call site than a bare `.dp` for what is an optical correction:
 * the unit sits on the value's baseline, not on its descender.
 */
private val Int.dpAsBaselineNudge get() =
    androidx.compose.ui.unit
        .Dp(this.toFloat())

@Preview(name = "Metrics", showBackground = true)
@Composable
private fun MetricPreview() {
    MeretTheme {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Metric("4.82", "km", "Distance")
            Metric("01:42:07", "", "Duration")
            Metric("1,204", "", "Points", compact = true)
        }
    }
}
