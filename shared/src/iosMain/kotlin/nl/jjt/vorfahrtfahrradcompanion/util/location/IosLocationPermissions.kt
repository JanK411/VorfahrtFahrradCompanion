package nl.jjt.vorfahrtfahrradcompanion.util.location

import androidx.compose.runtime.Composable

class IosLocationPermissions : LocationPermissions {
    @Composable
    override fun rememberState(): LocationPermissionState = TODO("iOS not implemented")
}
