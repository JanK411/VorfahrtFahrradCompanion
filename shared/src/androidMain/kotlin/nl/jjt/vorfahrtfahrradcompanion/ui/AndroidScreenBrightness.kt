package nl.jjt.vorfahrtfahrradcompanion.ui

import android.view.Window
import android.view.WindowManager

/**
 * Brightness is an attribute of the Activity's window, and one the platform scopes to it: it holds
 * only while the app is in front, and the device's own setting is what everything else gets.
 */
class AndroidScreenBrightness(private val window: Window) : ScreenBrightness {
    override fun set(level: Float?) {
        window.attributes = window.attributes.apply {
            screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}
