package nl.jjt.vorfahrtfahrradcompanion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.criteria.CriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.di.appModules
import nl.jjt.vorfahrtfahrradcompanion.location.LocationScreen
import nl.jjt.vorfahrtfahrradcompanion.navigation.LocalNavigationGate
import nl.jjt.vorfahrtfahrradcompanion.navigation.NavigationGate
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.AppTheme
import nl.jjt.vorfahrtfahrradcompanion.ui.BicycleIcon
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

private enum class Tab(val label: String, val icon: ImageVector) {
    Criteria("Criteria", Icons.AutoMirrored.Filled.List),
    Location("Ride", BicycleIcon),
    Settings("Settings", Icons.Filled.Settings),
}


@Composable
fun App(additionalModules: List<Module> = emptyList()) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules + additionalModules) }),
        content = {
            AppTheme {
                var selected by remember { mutableStateOf(Tab.Criteria) }
                // The screen on display may ask the user before it lets go; see NavigationGate.
                val gate = remember { NavigationGate() }
                var navigating by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                CompositionLocalProvider(LocalNavigationGate provides gate) {
                    Scaffold(
                        topBar = {
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
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    scrolledContainerColor = Color.Unspecified,
                                    navigationIconContentColor = Color.Unspecified,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    actionIconContentColor = Color.Unspecified
                                ),
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                Tab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = tab == selected,
                                        onClick = {
                                            if (tab != selected && !navigating) scope.launch {
                                                navigating = true
                                                if (gate.canLeave()) selected = tab
                                                navigating = false
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        val modifier = Modifier.fillMaxSize().padding(padding)
                        when (selected) {
                            Tab.Criteria -> CriteriaScreen(modifier)

                            Tab.Location -> LocationScreen(modifier)

                            Tab.Settings -> SettingsScreen(modifier)
                        }
                    }
                }
            }
        })
}
