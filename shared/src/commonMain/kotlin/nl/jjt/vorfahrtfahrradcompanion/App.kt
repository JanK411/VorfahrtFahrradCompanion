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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.criteria.CriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.di.appModules
import nl.jjt.vorfahrtfahrradcompanion.location.LocationScreen
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.AppTheme
import nl.jjt.vorfahrtfahrradcompanion.ui.BicycleIcon
import nl.jjt.vorfahrtfahrradcompanion.settings.SettingsViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.Module
import org.koin.dsl.koinConfiguration

private enum class Tab(val label: String, val icon: ImageVector) {
    Criteria("Criteria", Icons.AutoMirrored.Filled.List),
    Location("Ride", BicycleIcon),
    Settings("Settings", Icons.Filled.Settings),
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(additionalModules: List<Module> = emptyList()) {
    KoinApplication(
        configuration = koinConfiguration(declaration = { modules(appModules + additionalModules) }),
        content = {
            AppTheme {
                var selected by remember { mutableStateOf(Tab.Criteria) }
                // Hoisted so navigation can guard against leaving Settings with unsaved edits.
                val settingsViewModel: SettingsViewModel = koinViewModel()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                var pendingTab by remember { mutableStateOf<Tab?>(null) }
                var saving by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

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
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tab == selected,
                                    onClick = {
                                        if (tab != selected &&
                                            selected == Tab.Settings &&
                                            settingsState.hasUnsavedChanges
                                        ) {
                                            pendingTab = tab
                                        } else {
                                            selected = tab
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

                        Tab.Settings -> SettingsScreen(modifier, settingsViewModel)
                    }
                }

                pendingTab?.let { target ->
                    AlertDialog(
                        onDismissRequest = { if (!saving) pendingTab = null },
                        title = { Text("Unsaved changes") },
                        text = { Text("You have unsaved settings. Save them before leaving?") },
                        confirmButton = {
                            TextButton(
                                enabled = !saving,
                                onClick = {
                                    saving = true
                                    scope.launch {
                                        settingsViewModel.saveAndWait()
                                        saving = false
                                        selected = target
                                        pendingTab = null
                                    }
                                },
                            ) { Text(if (saving) "Saving…" else "Save") }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = !saving,
                                onClick = {
                                    settingsViewModel.discardChanges()
                                    selected = target
                                    pendingTab = null
                                },
                            ) { Text("Discard") }
                        },
                    )
                }
            }
        })
}
