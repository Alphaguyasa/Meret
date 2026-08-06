package et.alpha.meret.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import et.alpha.meret.R
import et.alpha.meret.core.designsystem.component.CrosshairGlyph
import et.alpha.meret.core.designsystem.component.LayersGlyph
import et.alpha.meret.core.designsystem.component.PinGlyph
import et.alpha.meret.core.designsystem.component.TrackGlyph

/**
 * The four places reachable from the bottom bar.
 *
 * Four, not six. Everything else is reached from the map, which is the home screen —
 * a tab bar the width of a thumb is a tab bar nobody hits correctly while walking.
 */
enum class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: @Composable (Modifier, Color, String?) -> Unit,
) {
    Map(
        route = MAP_ROUTE,
        labelRes = R.string.destination_map,
        icon = { modifier, tint, cd -> CrosshairGlyph(modifier, cd, tint) },
    ),
    Tracks(
        route = TRACKS_ROUTE,
        labelRes = R.string.destination_tracks,
        icon = { modifier, tint, cd -> TrackGlyph(modifier, cd, tint) },
    ),
    Waypoints(
        route = WAYPOINTS_ROUTE,
        labelRes = R.string.destination_waypoints,
        icon = { modifier, tint, cd -> PinGlyph(modifier, cd, tint) },
    ),
    Packs(
        route = TILE_PACKS_ROUTE,
        labelRes = R.string.destination_packs,
        icon = { modifier, tint, cd -> LayersGlyph(modifier, cd, tint) },
    ),
}

const val MAP_ROUTE: String = "map"
const val TRACKS_ROUTE: String = "tracks"
const val WAYPOINTS_ROUTE: String = "waypoints"
const val TILE_PACKS_ROUTE: String = "tile_packs"
const val SETTINGS_ROUTE: String = "settings"
