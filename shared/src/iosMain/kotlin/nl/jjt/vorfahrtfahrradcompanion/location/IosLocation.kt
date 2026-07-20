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
    override val isEnabled: Flow<Boolean> get() = TODO("iOS not implemented")
    override fun open(): Unit = TODO("iOS not implemented")
}
