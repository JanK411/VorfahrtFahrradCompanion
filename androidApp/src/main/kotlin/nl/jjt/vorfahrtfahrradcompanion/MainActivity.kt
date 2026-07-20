package nl.jjt.vorfahrtfahrradcompanion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.jjt.vorfahrtfahrradcompanion.location.AndroidLocationPermissions
import nl.jjt.vorfahrtfahrradcompanion.location.AndroidLocationProvider
import nl.jjt.vorfahrtfahrradcompanion.location.LocationPermissions
import nl.jjt.vorfahrtfahrradcompanion.location.LocationProvider
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val androidModule = module {
            single<Context> { applicationContext }
            single<LocationProvider> { AndroidLocationProvider(get()) }
            single<LocationPermissions> { AndroidLocationPermissions(get()) }
        }

        setContent {
            App(listOf(androidModule))
        }
    }
}
