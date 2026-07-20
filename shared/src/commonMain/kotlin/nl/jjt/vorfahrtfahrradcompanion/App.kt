package nl.jjt.vorfahrtfahrradcompanion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import nl.jjt.vorfahrtfahrradcompanion.di.appModules
import nl.jjt.vorfahrtfahrradcompanion.location.LocationScreen
import org.koin.compose.KoinApplication
import org.koin.core.module.Module

private enum class Tab(val label: String, val icon: ImageVector) {
    Dummy1("Dummy 1", Icons.Filled.Home),
    Location("Location", Icons.Filled.Place),
}

@Composable
fun App(additionalModules: List<Module> = emptyList()) = KoinApplication(
    application = { modules(appModules + additionalModules) },
) {
    MaterialTheme {
        var selected by remember { mutableStateOf(Tab.Dummy1) }
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
                Tab.Dummy1 -> Box(modifier, contentAlignment = Alignment.Center) {
                    Text("Hello 1", style = MaterialTheme.typography.headlineMedium)
                }

                Tab.Location -> LocationScreen(modifier)
            }
        }
    }
}
