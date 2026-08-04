package nl.jjt.vorfahrtfahrradcompanion.util.di

import android.content.Context
import android.view.Window
import nl.jjt.vorfahrtfahrradcompanion.db.createAppDatabase
import nl.jjt.vorfahrtfahrradcompanion.util.location.AndroidLocationPermissions
import nl.jjt.vorfahrtfahrradcompanion.util.location.AndroidLocationProvider
import nl.jjt.vorfahrtfahrradcompanion.util.location.AndroidLocationSettings
import nl.jjt.vorfahrtfahrradcompanion.util.location.LocationPermissions
import nl.jjt.vorfahrtfahrradcompanion.util.location.LocationProvider
import nl.jjt.vorfahrtfahrradcompanion.util.location.LocationSettings
import nl.jjt.vorfahrtfahrradcompanion.util.platform.AndroidScreenAwake
import nl.jjt.vorfahrtfahrradcompanion.util.platform.AndroidScreenBrightness
import nl.jjt.vorfahrtfahrradcompanion.util.platform.AndroidSystemBars
import nl.jjt.vorfahrtfahrradcompanion.util.platform.AndroidSystemCacheMarker
import nl.jjt.vorfahrtfahrradcompanion.util.platform.ScreenAwake
import nl.jjt.vorfahrtfahrradcompanion.util.platform.ScreenBrightness
import nl.jjt.vorfahrtfahrradcompanion.util.platform.SystemBars
import nl.jjt.vorfahrtfahrradcompanion.util.platform.SystemCacheMarker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Everything that cannot be built without the platform: a [Context] for the database and the
 * location services, and the [Window] the screen and system bars are properties of.
 *
 * It lives here rather than in the host activity so that the activity stays a plain adapter, and so
 * an iOS counterpart has a shape to copy.
 */
fun androidModule(context: Context, window: Window): Module = module {
    single<Context> { context }
    single<LocationProvider> { AndroidLocationProvider(get()) }
    single<LocationPermissions> { AndroidLocationPermissions(get()) }
    single<LocationSettings> { AndroidLocationSettings(get()) }
    single<ScreenAwake> { AndroidScreenAwake(window) }
    single<SystemBars> { AndroidSystemBars(window) }
    single<ScreenBrightness> { AndroidScreenBrightness(window) }
    single<SystemCacheMarker> { AndroidSystemCacheMarker(get()) }
    single { createAppDatabase(get()) }
}
