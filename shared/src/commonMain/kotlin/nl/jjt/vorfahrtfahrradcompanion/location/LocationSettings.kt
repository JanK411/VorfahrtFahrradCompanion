package nl.jjt.vorfahrtfahrradcompanion.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface LocationSettingsState {
    /** Whether the device's location services are switched on. Re-read when the app resumes. */
    val isEnabled: Boolean

    /** Sends the user to the system location settings. */
    fun open()
}

/** The platform settings check and hand-off only — the UI itself lives in commonMain. */
interface LocationSettings {
    @Composable
    fun rememberState(): LocationSettingsState
}
