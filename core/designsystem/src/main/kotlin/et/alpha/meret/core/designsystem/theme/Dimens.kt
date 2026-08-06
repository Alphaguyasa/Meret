package et.alpha.meret.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4 dp base scale. Nothing in this app should use a spacing value not named here. */
public object Spacing {
    public val xxs: Dp = 4.dp
    public val xs: Dp = 8.dp
    public val sm: Dp = 12.dp
    public val md: Dp = 16.dp
    public val lg: Dp = 24.dp
    public val xl: Dp = 32.dp
    public val xxl: Dp = 48.dp
}

/**
 * Sizes that exist for physical reasons rather than aesthetic ones.
 *
 * 48 dp is the Android floor for a touch target. It is not enough for someone holding
 * the phone one-handed, walking, after six hours in the field — so anything used while
 * moving gets [touchTargetField] instead.
 */
public object Sizes {
    /** Android minimum. Use only for controls touched while standing still. */
    public val touchTargetMin: Dp = 48.dp

    /** Minimum for any control used while walking. */
    public val touchTargetField: Dp = 56.dp

    /** The record FAB. Deliberately larger than a Material FAB. */
    public val recordButton: Dp = 72.dp

    /** Status dots, GPS bars, sync pips. */
    public val indicator: Dp = 10.dp

    public val iconSmall: Dp = 16.dp
    public val icon: Dp = 24.dp
    public val iconLarge: Dp = 32.dp

    /** Hairline used for overlay haloes and dividers. */
    public val hairline: Dp = 1.dp
}

/** Flat surfaces. Nothing rises above the FAB. */
public object Elevations {
    public val flat: Dp = 0.dp
    public val card: Dp = 1.dp
    public val sheet: Dp = 3.dp
    public val fab: Dp = 6.dp
}

internal val MeretShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    // Cards.
    medium = RoundedCornerShape(8.dp),
    // Bottom sheets.
    large = RoundedCornerShape(12.dp),
    // Dialogs.
    extraLarge = RoundedCornerShape(16.dp),
)
