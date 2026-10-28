package et.alpha.meret.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import et.alpha.meret.feature.map.MapRoute
import et.alpha.meret.feature.settings.diagnostics.DiagnosticsRoute
import et.alpha.meret.feature.settings.onboarding.OnboardingRoute
import et.alpha.meret.feature.tiles.TilePackScreen
import et.alpha.meret.feature.tracking.TrackListScreen
import et.alpha.meret.feature.waypoint.WaypointListScreen
import et.alpha.meret.navigation.DIAGNOSTICS_ROUTE
import et.alpha.meret.navigation.MAP_ROUTE
import et.alpha.meret.navigation.TILE_PACKS_ROUTE
import et.alpha.meret.navigation.TRACKS_ROUTE
import et.alpha.meret.navigation.TopLevelDestination
import et.alpha.meret.navigation.WAYPOINTS_ROUTE

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
    requireOnboarding: Boolean = true,
) {
    // Checked once per process. The permission screens come before the map because
    // without location there is nothing the map can do; every other step can be skipped.
    // Saveable, so a rotation does not re-run it, while a new process (where the worker may
    // have revoked something in Settings) does.
    var onboarded by rememberSaveable { mutableStateOf(!requireOnboarding) }
    // Diagnostics can run the flow again. Each run gets its own ViewModel: one kept from the
    // last run would still hold the skips the worker made then in memory.
    var onboardingRun by rememberSaveable { mutableIntStateOf(0) }

    if (onboarded) {
        MeretShell(
            onRecheckPermissions = {
                onboardingRun += 1
                onboarded = false
            },
            modifier = modifier,
        )
    } else {
        OnboardingRoute(
            onFinished = { onboarded = true },
            modifier = modifier.safeDrawingPadding(),
            viewModel = hiltViewModel(key = "onboarding-$onboardingRun"),
        )
    }
}

@Composable
private fun MeretShell(
    onRecheckPermissions: () -> Unit,
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
                    val scheme = MaterialTheme.colorScheme
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentDestination
                                ?.hierarchy
                                ?.any { it.route == destination.route } == true
                        val label = stringResource(destination.labelRes)

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                val tint =
                                    if (selected) {
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
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = scheme.primaryContainer,
                                    selectedIconColor = scheme.onPrimaryContainer,
                                    selectedTextColor = scheme.onSurface,
                                ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = MAP_ROUTE,
                ) {
                    // The map draws under the bottom bar on purpose — it is a map, and
                    // cropping it to a rectangle above the bar wastes the screen. It
                    // receives the padding so its own controls stay clear.
                    composable(MAP_ROUTE) {
                        MapRoute(
                            onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) },
                            contentPadding = innerPadding,
                        )
                    }
                    composable(TRACKS_ROUTE) {
                        TrackListScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    composable(WAYPOINTS_ROUTE) {
                        WaypointListScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    // Reached from the GPS chip on the map, not the bottom bar.
                    composable(DIAGNOSTICS_ROUTE) {
                        DiagnosticsRoute(
                            onRecheckPermissions = onRecheckPermissions,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    composable(TILE_PACKS_ROUTE) {
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
    MeretTheme { MeretApp(requireOnboarding = false) }
}
