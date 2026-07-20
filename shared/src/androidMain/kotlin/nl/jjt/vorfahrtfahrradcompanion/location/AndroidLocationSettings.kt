package nl.jjt.vorfahrtfahrradcompanion.location

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class AndroidLocationSettings(private val context: Context) : LocationSettings {

    @Composable
    override fun rememberState(): LocationSettingsState {
        val enabled = remember { mutableStateOf(isEnabled()) }
        // The user leaves the app to flip the switch, so re-read on every resume.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled.value = isEnabled() }

        return remember {
            object : LocationSettingsState {
                override val isEnabled get() = enabled.value
                override fun open() = context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun isEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
