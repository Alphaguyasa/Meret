package et.alpha.meret.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Line heights are deliberately looser than Material's defaults. Ethiopic has a taller
 * x-height than Latin and clips or crowds at 24 sp leading — `bodyLarge` is 16/26, not
 * 16/24, for that reason. `LineHeightStyle.Trim.None` keeps the extra leading on the
 * first and last line so mixed Latin/Ethiopic paragraphs do not shift.
 *
 * Fonts: the intended stack is Inter for Latin plus Noto Sans Ethiopic, resolved as one
 * [FontFamily] so a mixed string renders consistently. Until the .ttf files are dropped
 * into `core/designsystem/src/main/res/font/`, this falls back to the platform family —
 * which on every target device already includes an Ethiopic face, so Amharic renders
 * correctly today; only the Latin half is not yet on-brand. Downloadable fonts are not
 * an option: this app must render identically with the radio off.
 */
public val MeretFontFamily: FontFamily = FontFamily.Default

private val LineHeightNoTrim =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

private fun meretStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = MeretFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = LineHeightNoTrim,
    // "tnum" fixes digit advance width. Without it a ticking duration re-lays-out once
    // a second, for eight hours, and the whole bar jitters.
    fontFeatureSettings = if (tabular) "tnum" else null,
)

internal val MeretTypography =
    Typography(
        displayLarge = meretStyle(45, 54, FontWeight.SemiBold, letterSpacing = -0.5),
        displayMedium = meretStyle(38, 46, FontWeight.SemiBold, letterSpacing = -0.4),
        displaySmall = meretStyle(32, 40, FontWeight.SemiBold, letterSpacing = -0.3),
        headlineLarge = meretStyle(30, 38, FontWeight.SemiBold),
        headlineMedium = meretStyle(26, 34, FontWeight.SemiBold),
        headlineSmall = meretStyle(24, 32, FontWeight.SemiBold),
        titleLarge = meretStyle(20, 28, FontWeight.SemiBold),
        titleMedium = meretStyle(16, 24, FontWeight.SemiBold, letterSpacing = 0.1),
        titleSmall = meretStyle(14, 22, FontWeight.SemiBold, letterSpacing = 0.1),
        bodyLarge = meretStyle(16, 26, FontWeight.Normal, letterSpacing = 0.15),
        bodyMedium = meretStyle(14, 22, FontWeight.Normal, letterSpacing = 0.2),
        bodySmall = meretStyle(12, 18, FontWeight.Normal, letterSpacing = 0.3),
        labelLarge = meretStyle(14, 20, FontWeight.SemiBold, letterSpacing = 0.1),
        labelMedium = meretStyle(12, 16, FontWeight.SemiBold, letterSpacing = 0.4),
        labelSmall = meretStyle(11, 16, FontWeight.SemiBold, letterSpacing = 0.5),
    )

/**
 * Styles Material has no slot for. Field workers do not trust spinners — they trust
 * numbers — so the numeric styles are a first-class part of the scale, not an ad-hoc
 * `copy()` at the call site.
 */
public data class MeretExtendedTypography(
    /** Big live metrics: distance, duration, area. */
    val metricLarge: TextStyle,
    /** Secondary metrics inside chips and list rows. */
    val metricSmall: TextStyle,
    /** The unit that follows a metric — "km", "ha", "m²". */
    val metricUnit: TextStyle,
    /** Fixed-width detail: coordinates, device ids, diagnostics. */
    val mono: TextStyle,
)

internal val MeretExtendedTypographyDefaults =
    MeretExtendedTypography(
        metricLarge = meretStyle(28, 34, FontWeight.Bold, letterSpacing = -0.2, tabular = true),
        metricSmall = meretStyle(18, 24, FontWeight.SemiBold, tabular = true),
        metricUnit = meretStyle(13, 18, FontWeight.Medium, letterSpacing = 0.3),
        mono =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                lineHeightStyle = LineHeightNoTrim,
            ),
    )
