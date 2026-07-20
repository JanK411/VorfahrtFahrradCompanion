package nl.jjt.vorfahrtfahrradcompanion.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocationScreen(modifier: Modifier = Modifier) {
    val permissions: LocationPermissions = koinInject()
    val permissionState = permissions.rememberState()

    if (!permissionState.isGranted) {
        LocationPermissionRationale(onRequest = permissionState::request, modifier = modifier)
        return
    }

    val settingsState = koinInject<LocationSettings>().rememberState()

    val viewModel: LocationViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            LocationUiState.Waiting -> if (settingsState.isEnabled) {
                Text("Waiting for a GPS fix…")
            } else {
                Text("Location services are switched off.")
                Button(onClick = settingsState::open) { Text("Turn on location") }
            }
            is LocationUiState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is LocationUiState.Fix -> {
                Text("${s.location.latitude}, ${s.location.longitude}", style = MaterialTheme.typography.headlineSmall)
                Text("± ${s.location.accuracyMeters} m")
                s.location.speedMetersPerSecond?.let { Text("${it * 3.6f} km/h") }
                Text(s.location.timestamp.toString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LocationPermissionRationale(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This app needs precise location access to track your ride.")
        Button(onClick = onRequest) { Text("Grant location permission") }
    }
}
