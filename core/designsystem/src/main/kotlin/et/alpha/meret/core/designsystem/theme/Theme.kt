package et.alpha.meret.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMeretSemanticColors = staticCompositionLocalOf { LightSemanticColors }
private val LocalMeretExtendedTypography =
    staticCompositionLocalOf { MeretExtendedTypographyDefaults }

/**
 * The app theme.
 *
 * There is no dynamic-colour path. Material You would recolour GPS quality and recording
 * state from the user's wallpaper, and those colours carry meaning that has to be the
 * same on every device in a team — a supervisor pointing at a red bar on one phone must
 * mean the same thing on another.
 */
@Composable
public fun MeretTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MeretDarkColorScheme else MeretLightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(
        LocalMeretSemanticColors provides semanticColors,
        LocalMeretExtendedTypography provides MeretExtendedTypographyDefaults,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeretTypography,
            shapes = MeretShapes,
            content = content,
        )
    }
}

/** Accessors for the parts of the theme Material does not model. */
public object MeretTheme {
    /** Recording, GPS, sync and map-overlay colours. */
    public val colors: MeretSemanticColors
        @Composable @ReadOnlyComposable
        get() = LocalMeretSemanticColors.current

    /** Metric and monospace styles. */
    public val typography: MeretExtendedTypography
        @Composable @ReadOnlyComposable
        get() = LocalMeretExtendedTypography.current
}
