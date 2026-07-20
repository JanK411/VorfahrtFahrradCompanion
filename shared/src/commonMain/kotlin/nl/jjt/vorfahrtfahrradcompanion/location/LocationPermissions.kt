package nl.jjt.vorfahrtfahrradcompanion.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface LocationPermissionState {
    val isGranted: Boolean
    fun request()
}

/** The platform request mechanism only — the permission UI itself lives in commonMain. */
interface LocationPermissions {
    @Composable
    fun rememberState(): LocationPermissionState
}
