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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import nl.jjt.vorfahrtfahrradcompanion.criteria.CriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.di.appModules
import nl.jjt.vorfahrtfahrradcompanion.location.LocationScreen
import nl.jjt.vorfahrtfahrradcompanion.navigation.LocalNavigationGate
import nl.jjt.vorfahrtfahrradcompanion.navigation.NavigationGate
import nl.jjt.vorfahrtfahrradcompanion.patchnotes.PatchNotesScreen
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.AppTheme
import nl.jjt.vorfahrtfahrradcompanion.ui.BicycleIcon
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

@Serializable private data object CriteriaRoute
@Serializable private data object RideRoute
@Serializable private data object SettingsRoute
@Serializable private data object PatchNotesRoute

/** Bottom-bar destinations. [PatchNotesRoute] is a sub-page reached from Settings, not a tab. */
private enum class Tab(val label: String, val icon: ImageVector, val route: Any) {
    Criteria("Criteria", Icons.AutoMirrored.Filled.List, CriteriaRoute),
    Location("Ride", BicycleIcon, RideRoute),
    Settings("Settings", Icons.Filled.Settings, SettingsRoute),
}

@Composable
fun App(additionalModules: List<Module> = emptyList()) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules + additionalModules) }),
        content = {
            AppTheme {
                val navController = rememberNavController()
                // The screen on display may ask the user before it lets go; see NavigationGate.
                val gate = remember { NavigationGate() }
                var navigating by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val currentDestination by navController.currentBackStackEntryAsState()
                val onSubPage = currentDestination?.destination?.hasRoute(PatchNotesRoute::class) == true

                val topBarColors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = Color.Unspecified,
                )

                CompositionLocalProvider(LocalNavigationGate provides gate) {
                    Scaffold(
                        topBar = {
                            if (onSubPage) {
                                CenterAlignedTopAppBar(
                                    title = { Text("What's New") },
                                    navigationIcon = {
                                        IconButton(onClick = { navController.navigateUp() }) {
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
                            if (!onSubPage) {
                                NavigationBar {
                                    val destination = currentDestination?.destination
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
                            composable<CriteriaRoute> { CriteriaScreen(Modifier.fillMaxSize()) }

                            composable<RideRoute> { LocationScreen(Modifier.fillMaxSize()) }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    onOpenPatchNotes = { navController.navigate(PatchNotesRoute) },
                                )
                            }

                            composable<PatchNotesRoute> { PatchNotesScreen(Modifier.fillMaxSize()) }
                        }
                    }
                }
            }
        })
}
