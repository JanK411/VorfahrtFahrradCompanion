package nl.jjt.vorfahrtfahrradcompanion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.Segment
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.SegmentRecorder
import nl.jjt.vorfahrtfahrradcompanion.ui.common.DimWhenIdle
import nl.jjt.vorfahrtfahrradcompanion.ui.criteria.jan.JanCriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.location.LocationScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.navigation.*
import nl.jjt.vorfahrtfahrradcompanion.ui.patchnotes.PatchNotesScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.rides.RidesScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.settings.ServerConnectionScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.settings.SettingsScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.theme.AppTheme
import nl.jjt.vorfahrtfahrradcompanion.ui.theme.BicycleIcon
import nl.jjt.vorfahrtfahrradcompanion.util.daylight.Daylight
import nl.jjt.vorfahrtfahrradcompanion.util.di.appModules
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

/** Bottom-bar destinations. [PatchNotesRoute] is a sub-page reached from Settings, not a tab. */
private enum class Tab(val label: String, val icon: ImageVector, val route: Any) {
    CRITERIA("Criteria", Icons.AutoMirrored.Filled.List, CriteriaRoute),
    RIDES("Rides", BicycleIcon, RidesRoute),
    SETTINGS("Settings", Icons.Filled.Settings, SettingsRoute),
}

@Composable
fun App(additionalModules: List<Module> = emptyList()) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules + additionalModules) }),
        content = {
            // Collected here rather than inside the theme: it stops while the app is in the
            // background, where nobody is looking at the colours anyway.
            val night by koinInject<Daylight>().isNight.collectAsStateWithLifecycle(false)

            AppTheme(night) {
                val navController = rememberNavController()
                // The screen on display may ask the user before it lets go; see NavigationGate.
                val gate = remember { NavigationGate() }
                var navigating by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val currentDestination by navController.currentBackStackEntryAsState()
                val destination = currentDestination?.destination
                val subPage = subPages.firstOrNull { destination?.hasRoute(it::class) == true }
                val onSubPage = subPage != null

                // A running segment claims the whole screen: room for the criteria, and no tab to hit
                // by accident. Tied to the Criteria route so no other screen can ever end up tab-less.
                val draft by koinInject<SegmentRecorder>().draft.collectAsStateWithLifecycle()
                val recording =
                    destination?.hasRoute(CriteriaRoute::class) == true && draft.segment is Segment.Open

                val topBarColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = Color.Unspecified,
                )

                // Only while a segment runs: that is where the screen is held awake for minutes on
                // end with nothing to answer, and where the device's own dimming is out of the way.
                DimWhenIdle(enabled = recording) {
                    CompositionLocalProvider(LocalNavigationGate provides gate) {
                        Scaffold(
                            topBar = {
                                if (onSubPage) {
                                    CenterAlignedTopAppBar(
                                        title = { Text(subPage.title) },
                                        navigationIcon = {
                                            IconButton(onClick = {
                                                if (!navigating) scope.launch {
                                                    navigating = true
                                                    if (gate.canLeave()) navController.navigateUp()
                                                    navigating = false
                                                }
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        },
                                        colors = topBarColors,
                                    )
                                } else {
                                    CenterAlignedTopAppBar(
                                        title = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(BicycleIcon, contentDescription = null)
                                                Text("Vorfahrt Companion")
                                            }
                                        },
                                        colors = topBarColors,
                                    )
                                }
                            },
                            bottomBar = {
                                if (!onSubPage && !recording) {
                                    NavigationBar {
                                        Tab.entries.forEach { tab ->
                                            val selected =
                                                destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    if (!selected && !navigating) scope.launch {
                                                        navigating = true
                                                        if (gate.canLeave()) {
                                                            navController.navigate(tab.route) {
                                                                popUpTo(CriteriaRoute) { saveState = true }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                        navigating = false
                                                    }
                                                },
                                                icon = { Icon(tab.icon, contentDescription = null) },
                                                label = { Text(tab.label) },
                                            )
                                        }
                                    }
                                }
                            },
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = CriteriaRoute,
                                modifier = Modifier.fillMaxSize().padding(padding),
                            ) {
                                composable<CriteriaRoute> { JanCriteriaScreen(Modifier.fillMaxSize()) }

                                composable<RidesRoute> { RidesScreen(Modifier.fillMaxSize()) }

                                composable<SettingsRoute> {
                                    SettingsScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onOpenServerConnection = { navController.navigate(ServerConnectionRoute) },
                                        onOpenLocation = { navController.navigate(LocationRoute) },
                                        onOpenPatchNotes = { navController.navigate(PatchNotesRoute) },
                                    )
                                }

                                composable<LocationRoute> { LocationScreen(Modifier.fillMaxSize()) }

                                composable<ServerConnectionRoute> {
                                    ServerConnectionScreen(
                                        modifier = Modifier.fillMaxSize(),
                                        onNavigateUp = { navController.navigateUp() },
                                    )
                                }

                                composable<PatchNotesRoute> { PatchNotesScreen(Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
            }
        })
}
