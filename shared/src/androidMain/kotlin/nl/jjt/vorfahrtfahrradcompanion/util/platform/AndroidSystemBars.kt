package nl.jjt.vorfahrtfahrradcompanion.util.platform

import android.view.Window
import androidx.core.view.WindowCompat

/** The bar appearance is a property of the Activity's window, so that is what this holds. */
class AndroidSystemBars(private val window: Window) : SystemBars {
    override fun iconsFor(dark: Boolean) {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightStatusBars = !dark
        bars.isAppearanceLightNavigationBars = !dark
    }
}
