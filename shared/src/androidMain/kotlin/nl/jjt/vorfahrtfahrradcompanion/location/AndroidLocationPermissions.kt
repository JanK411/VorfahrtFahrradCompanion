package nl.jjt.vorfahrtfahrradcompanion.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

private val REQUIRED = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

class AndroidLocationPermissions(private val context: Context) : LocationPermissions {

    @Composable
    override fun rememberState(): LocationPermissionState {
        val granted = remember { mutableStateOf(isGranted()) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted.value = isGranted() }

        return remember(launcher) {
            object : LocationPermissionState {
                override val isGranted get() = granted.value
                override fun request() = launcher.launch(REQUIRED)
            }
        }
    }

    private fun isGranted() = REQUIRED.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}
