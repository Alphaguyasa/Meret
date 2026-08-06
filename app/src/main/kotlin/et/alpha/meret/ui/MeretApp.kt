package et.alpha.meret.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import et.alpha.meret.core.designsystem.component.OfflineIndicator
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.core.designsystem.theme.Sizes
import et.alpha.meret.feature.map.MapScreen
import et.alpha.meret.feature.map.MapUiState
import et.alpha.meret.feature.tiles.TilePackScreen
import et.alpha.meret.feature.tracking.TrackListScreen
import et.alpha.meret.feature.waypoint.WaypointListScreen
import et.alpha.meret.navigation.MapRoute
import et.alpha.meret.navigation.TilePacksRoute
import et.alpha.meret.navigation.TopLevelDestination
import et.alpha.meret.navigation.TracksRoute
import et.alpha.meret.navigation.WaypointsRoute

/**
 * App shell: offline strip, content, bottom bar.
 *
 * The offline strip sits above the content rather than floating over it, so it never
 * covers a control. It is the first thing on the screen because the user's first
 * question, always, is whether the device is working.
 */
@Composable
fun MeretApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Real connectivity observation arrives with :core:network. Offline is the honest
    // default until then — and the expected state in the field regardless.
    val isOffline = remember { true }

    Column(modifier = modifier.fillMaxSize()) {
        OfflineIndicator(visible = isOffline)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        val label = stringResource(destination.labelRes)

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                val tint = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                destination.icon(Modifier.size(Sizes.icon), tint, null)
                            },
                            // Labels always shown. An icon-only bar is a guessing game
                            // in Amharic, where the glyphs carry no learned meaning.
                            label = { Text(label, maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = MapRoute,
                ) {
                    // The map draws under the bottom bar on purpose — it is a map, and
                    // cropping it to a rectangle above the bar wastes the screen. It
                    // receives the padding so its own controls stay clear.
                    composable(MapRoute) {
                        MapScreen(
                            state = MapUiState(),
                            onRecordTap = {},
                            onRecordLongPress = {},
                            onAddWaypoint = {},
                            onRecentreMap = {},
                            onOpenSync = {},
                            onOpenDiagnostics = {},
                            contentPadding = innerPadding,
                        )
                    }
                    composable(TracksRoute) {
                        TrackListScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    composable(WaypointsRoute) {
                        WaypointListScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    composable(TilePacksRoute) {
                        TilePackScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-top navigation that pops back to the start destination and restores state.
 *
 * Without `restoreState` a field worker who checks the track list and returns to the map
 * loses their pan position, which on a screen they look at every ninety seconds is
 * infuriating.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}


@Preview(showBackground = true, widthDp = 390, heightDp = 800)
@Composable
private fun MeretAppPreview() {
    MeretTheme { MeretApp() }
}
