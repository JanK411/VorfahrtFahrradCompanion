package nl.jjt.vorfahrtfahrradcompanion.location

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

class IosLocationProvider : LocationProvider {
    override fun locations(intervalMillis: Long): Flow<Location> = TODO("iOS not implemented")
}

class IosLocationPermissions : LocationPermissions {
    @Composable
    override fun rememberState(): LocationPermissionState = TODO("iOS not implemented")
}

class IosLocationSettings : LocationSettings {
    @Composable
    override fun rememberState(): LocationSettingsState = TODO("iOS not implemented")
}
