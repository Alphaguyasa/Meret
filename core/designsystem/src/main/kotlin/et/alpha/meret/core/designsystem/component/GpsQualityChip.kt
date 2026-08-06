package et.alpha.meret.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import et.alpha.meret.core.designsystem.R
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.core.designsystem.theme.Spacing

/**
 * How good the current fix is, as four buckets rather than a raw number. Thresholds are
 * from DESIGN_SYSTEM.md and are the only place they should be written down.
 */
public enum class GpsQuality {
    /** Accuracy <= 5 m. */
    Excellent,

    /** Accuracy <= 10 m. */
    Good,

    /** Accuracy <= 25 m. */
    Fair,

    /** Accuracy > 25 m. */
    Poor,

    /** No fix at all. */
    NoFix,
    ;

    /** Bars to fill, out of four. */
    internal val filledBars: Int
        get() =
            when (this) {
                Excellent -> 4
                Good -> 3
                Fair -> 2
                Poor -> 1
                NoFix -> 0
            }

    public companion object {
        /** Buckets a horizontal accuracy in metres. `null` means no fix. */
        public fun fromAccuracyMetres(accuracyMetres: Float?): GpsQuality = when {
            accuracyMetres == null -> NoFix
            accuracyMetres <= EXCELLENT_MAX_METRES -> Excellent
            accuracyMetres <= GOOD_MAX_METRES -> Good
            accuracyMetres <= FAIR_MAX_METRES -> Fair
            else -> Poor
        }

        private const val EXCELLENT_MAX_METRES = 5f
        private const val GOOD_MAX_METRES = 10f
        private const val FAIR_MAX_METRES = 25f
    }
}

/**
 * Persistent GPS readout for the map screen.
 *
 * Colour is never the signal on its own here: the bar count carries the same
 * information, which survives direct sunlight when hue does not. The accuracy figure is
 * always shown, because a field worker will trust "±4 m" and will not trust a green dot.
 */
@Composable
public fun GpsQualityChip(
    quality: GpsQuality,
    accuracyMetres: Float?,
    modifier: Modifier = Modifier,
    satelliteCount: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MeretTheme.colors
    val barColor =
        when (quality) {
            GpsQuality.Excellent -> colors.gpsExcellent
            GpsQuality.Good -> colors.gpsGood
            GpsQuality.Fair -> colors.gpsFair
            GpsQuality.Poor, GpsQuality.NoFix -> colors.gpsPoor
        }

    val accuracyLabel =
        accuracyMetres
            ?.let { stringResource(R.string.gps_accuracy_metres, it.toInt()) }
            ?: stringResource(R.string.gps_no_fix)

    val qualityLabel =
        stringResource(
            when (quality) {
                GpsQuality.Excellent -> R.string.gps_quality_excellent
                GpsQuality.Good -> R.string.gps_quality_good
                GpsQuality.Fair -> R.string.gps_quality_fair
                GpsQuality.Poor -> R.string.gps_quality_poor
                GpsQuality.NoFix -> R.string.gps_no_fix
            },
        )

    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = Sizes.touchTargetMin)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
                    RoundedCornerShape(percent = FULLY_ROUNDED),
                ).border(
                    Sizes.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(percent = FULLY_ROUNDED),
                ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                // One announcement for the whole chip. Reading out four separate bars and a
                // number is noise.
                .clearAndSetSemantics {
                    contentDescription = "$qualityLabel, $accuracyLabel"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SignalBars(
            filledBars = quality.filledBars,
            filledColor = barColor,
            emptyColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(Sizes.icon),
        )
        Text(
            text = accuracyLabel,
            style = MeretTheme.typography.metricSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (satelliteCount != null) {
            Text(
                text = stringResource(R.string.gps_satellite_count, satelliteCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Four ascending bars. Drawn rather than pulled from an icon font so it scales cleanly. */
@Composable
private fun SignalBars(
    filledBars: Int,
    filledColor: Color,
    emptyColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val gap = size.width * BAR_GAP_FRACTION
        val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        val radius = CornerRadius(barWidth / 2, barWidth / 2)

        repeat(BAR_COUNT) { index ->
            val heightFraction =
                SHORTEST_BAR_FRACTION +
                    (1f - SHORTEST_BAR_FRACTION) * (index / (BAR_COUNT - 1f))
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = if (index < filledBars) filledColor else emptyColor,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

private const val BAR_COUNT = 4
private const val BAR_GAP_FRACTION = 0.09f
private const val SHORTEST_BAR_FRACTION = 0.35f
private const val SURFACE_ALPHA = 0.92f
private const val FULLY_ROUNDED = 50

@Preview(name = "GPS quality", showBackground = true)
@Composable
private fun GpsQualityChipPreview() {
    MeretTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GpsQualityChip(GpsQuality.Excellent, 3f, satelliteCount = 14)
            GpsQualityChip(GpsQuality.Good, 8f, satelliteCount = 9)
            GpsQualityChip(GpsQuality.Fair, 19f, satelliteCount = 6)
            GpsQualityChip(GpsQuality.Poor, 42f, satelliteCount = 4)
            GpsQualityChip(GpsQuality.NoFix, null)
        }
    }
}
