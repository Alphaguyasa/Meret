package et.alpha.meret.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Palette for Meret.
 *
 * Light is the theme that matters — this app is used outdoors in direct equatorial sun.
 * Dark exists for evening review, not for field work, so it is tuned for legibility
 * rather than for looking moody.
 *
 * Hue is never the only carrier of meaning. Sunlight washes out hue long before it
 * washes out luminance, so every semantic colour here is paired with an icon or a label
 * at the call site.
 */

// ---------------------------------------------------------------------------
// Brand
// ---------------------------------------------------------------------------

/** Deep green. *Meret* is "land" in Amharic. */
private val Green100 = Color(0xFFC8E6DA)
private val Green200 = Color(0xFF8FD3BB)
private val Green700 = Color(0xFF1B5E4A)
private val Green800 = Color(0xFF00513C)
private val Green900 = Color(0xFF06281D)

/** Ochre — Ethiopian earth. */
private val Ochre100 = Color(0xFFFDE6C8)
private val Ochre300 = Color(0xFFF5B871)
private val Ochre700 = Color(0xFFB45309)
private val Ochre800 = Color(0xFF6A3800)
private val Ochre900 = Color(0xFF3A1A00)

/** Deep blue — water and administrative boundaries. */
private val Blue100 = Color(0xFFD2E1F5)
private val Blue300 = Color(0xFFA9C7EC)
private val Blue700 = Color(0xFF1E3A5F)
private val Blue800 = Color(0xFF24405F)
private val Blue900 = Color(0xFF0A1A2E)

private val Red100 = Color(0xFFFEE2E2)
private val Red300 = Color(0xFFFFB4AB)
private val Red600 = Color(0xFFDC2626)
private val Red800 = Color(0xFF93000A)
private val Red900 = Color(0xFF450A0A)

// Neutrals carry a faint green cast so white surfaces do not read as blue-cold next to
// the brand green.
private val NeutralWhite = Color(0xFFFFFFFF)
private val Neutral02 = Color(0xFFFBFCFB)
private val Neutral05 = Color(0xFFF6F8F6)
private val Neutral10 = Color(0xFFF0F3F1)
private val Neutral15 = Color(0xFFEAEEEB)
private val Neutral20 = Color(0xFFE4E9E6)
private val Neutral30 = Color(0xFFC3CBC7)
private val Neutral50 = Color(0xFF717975)
private val Neutral70 = Color(0xFF414A46)
private val Neutral85 = Color(0xFF26302C)
private val Neutral90 = Color(0xFF1A211E)
private val Neutral95 = Color(0xFF111815)

private val DarkSurfaceLowest = Color(0xFF090F0D)
private val DarkSurface = Color(0xFF0E1512)
private val DarkSurfaceLow = Color(0xFF161D1A)
private val DarkSurfaceHigh = Color(0xFF242B28)
private val DarkSurfaceHighest = Color(0xFF2F3633)
private val DarkOnSurface = Color(0xFFDEE4E0)
private val DarkOnSurfaceVariant = Color(0xFFC0C9C4)
private val DarkOutline = Color(0xFF8A938E)

internal val MeretLightColorScheme =
    lightColorScheme(
        primary = Green700,
        onPrimary = NeutralWhite,
        primaryContainer = Green100,
        onPrimaryContainer = Green900,
        inversePrimary = Green200,
        secondary = Ochre700,
        onSecondary = NeutralWhite,
        secondaryContainer = Ochre100,
        onSecondaryContainer = Ochre900,
        tertiary = Blue700,
        onTertiary = NeutralWhite,
        tertiaryContainer = Blue100,
        onTertiaryContainer = Blue900,
        error = Red600,
        onError = NeutralWhite,
        errorContainer = Red100,
        onErrorContainer = Red900,
        background = Neutral02,
        onBackground = Neutral95,
        surface = NeutralWhite,
        onSurface = Neutral95,
        surfaceVariant = Neutral20,
        onSurfaceVariant = Neutral70,
        surfaceTint = Green700,
        inverseSurface = Neutral85,
        inverseOnSurface = Neutral10,
        outline = Neutral50,
        outlineVariant = Neutral30,
        scrim = Color(0xFF000000),
        surfaceBright = NeutralWhite,
        surfaceDim = Neutral20,
        surfaceContainerLowest = NeutralWhite,
        surfaceContainerLow = Neutral05,
        surfaceContainer = Neutral10,
        surfaceContainerHigh = Neutral15,
        surfaceContainerHighest = Neutral20,
    )

internal val MeretDarkColorScheme =
    darkColorScheme(
        primary = Green200,
        onPrimary = Color(0xFF00382A),
        primaryContainer = Green800,
        onPrimaryContainer = Color(0xFFABF0D7),
        inversePrimary = Green700,
        secondary = Ochre300,
        onSecondary = Color(0xFF4A2600),
        secondaryContainer = Ochre800,
        onSecondaryContainer = Color(0xFFFFDDB8),
        tertiary = Blue300,
        onTertiary = Color(0xFF0A2540),
        tertiaryContainer = Blue800,
        onTertiaryContainer = Blue100,
        error = Red300,
        onError = Color(0xFF690005),
        errorContainer = Red800,
        onErrorContainer = Color(0xFFFFDAD6),
        background = DarkSurface,
        onBackground = DarkOnSurface,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = Neutral70,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = Green200,
        inverseSurface = DarkOnSurface,
        inverseOnSurface = Neutral90,
        outline = DarkOutline,
        outlineVariant = Neutral70,
        scrim = Color(0xFF000000),
        surfaceBright = DarkSurfaceHighest,
        surfaceDim = DarkSurfaceLowest,
        surfaceContainerLowest = DarkSurfaceLowest,
        surfaceContainerLow = DarkSurfaceLow,
        surfaceContainer = Neutral90,
        surfaceContainerHigh = DarkSurfaceHigh,
        surfaceContainerHighest = DarkSurfaceHighest,
    )

// ---------------------------------------------------------------------------
// Semantic colours
// ---------------------------------------------------------------------------

/**
 * Colours that mean something specific — recording state, GPS quality, sync state, map
 * overlays. These are not Material roles, so they live in their own holder reached
 * through [MeretTheme.colors].
 *
 * Every one of these is a *signal*, and per the design rules a signal is never
 * expressed by colour alone.
 */
public data class MeretSemanticColors(
    // Recording status
    val tracking: Color,
    val paused: Color,
    val stopped: Color,
    val danger: Color,
    // GPS quality — always drawn with a signal-bar icon beside it
    val gpsExcellent: Color,
    val gpsGood: Color,
    val gpsFair: Color,
    val gpsPoor: Color,
    // Sync
    val syncPending: Color,
    val syncActive: Color,
    val syncDone: Color,
    val syncConflict: Color,
    val syncFailed: Color,
    // Map overlays
    val trackActive: Color,
    val trackHistoric: Color,
    val trackPaused: Color,
    val waypoint: Color,
    val assignment: Color,
    val coverage: Color,
    val measureVertex: Color,
    /** Every map overlay needs this halo. A green line on green landcover is invisible. */
    val overlayHalo: Color,
)

internal val LightSemanticColors =
    MeretSemanticColors(
        tracking = Color(0xFF16A34A),
        paused = Color(0xFFD97706),
        stopped = Color(0xFF64748B),
        danger = Color(0xFFDC2626),
        gpsExcellent = Color(0xFF16A34A),
        gpsGood = Color(0xFF65A30D),
        gpsFair = Color(0xFFD97706),
        gpsPoor = Color(0xFFDC2626),
        syncPending = Color(0xFF64748B),
        syncActive = Color(0xFF2563EB),
        syncDone = Color(0xFF16A34A),
        syncConflict = Color(0xFFB45309),
        syncFailed = Color(0xFFDC2626),
        trackActive = Color(0xFF16A34A),
        trackHistoric = Color(0xFF1E3A5F),
        trackPaused = Color(0xFF94A3B8),
        waypoint = Color(0xFFB45309),
        assignment = Color(0xFF1E3A5F),
        coverage = Color(0xFF16A34A),
        measureVertex = Color(0xFFDC2626),
        overlayHalo = Color(0xFFFFFFFF),
    )

/**
 * Dark-theme semantics are lifted in lightness, not just reused. The light values sit at
 * 3:1 or worse against a near-black surface.
 */
internal val DarkSemanticColors =
    MeretSemanticColors(
        tracking = Color(0xFF4ADE80),
        paused = Color(0xFFFBBF24),
        stopped = Color(0xFF94A3B8),
        danger = Color(0xFFF87171),
        gpsExcellent = Color(0xFF4ADE80),
        gpsGood = Color(0xFFA3E635),
        gpsFair = Color(0xFFFBBF24),
        gpsPoor = Color(0xFFF87171),
        syncPending = Color(0xFF94A3B8),
        syncActive = Color(0xFF60A5FA),
        syncDone = Color(0xFF4ADE80),
        syncConflict = Color(0xFFF5B871),
        syncFailed = Color(0xFFF87171),
        trackActive = Color(0xFF4ADE80),
        trackHistoric = Color(0xFFA9C7EC),
        trackPaused = Color(0xFF94A3B8),
        waypoint = Color(0xFFF5B871),
        assignment = Color(0xFFA9C7EC),
        coverage = Color(0xFF4ADE80),
        measureVertex = Color(0xFFF87171),
        overlayHalo = Color(0xFF0E1512),
    )
