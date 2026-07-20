package nl.jjt.vorfahrtfahrradcompanion

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import nl.jjt.vorfahrtfahrradcompanion.criteria.CriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.di.appModules
import nl.jjt.vorfahrtfahrradcompanion.location.LocationScreen
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsScreen
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

private enum class Tab(val label: String, val icon: ImageVector) {
    Criteria("Criteria", Icons.AutoMirrored.Filled.List),
    Location("Location", Icons.Filled.Place),
    Settings("Settings", Icons.Filled.Settings),
}


@Composable
fun App(additionalModules: List<Module> = emptyList()) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules + additionalModules) }),
        content = {
            MaterialTheme {
                var selected by remember { mutableStateOf(Tab.Criteria) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == selected,
                                    onClick = { selected = tab },
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
        })
}
