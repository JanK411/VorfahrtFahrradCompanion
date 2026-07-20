package nl.jjt.vorfahrtfahrradcompanion.location

import kotlinx.coroutines.flow.Flow

/** The platform settings check and hand-off only — the UI itself lives in commonMain. */
interface LocationSettings {
    /** Emits the current state of the device's location services, then on every change. */
    val isEnabled: Flow<Boolean>

    /** Sends the user to the system location settings. */
    fun open()
}
