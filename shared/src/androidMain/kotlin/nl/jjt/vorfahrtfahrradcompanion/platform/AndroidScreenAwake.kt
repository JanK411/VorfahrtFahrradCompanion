package nl.jjt.vorfahrtfahrradcompanion.platform

import android.view.Window
import android.view.WindowManager

/** The keep-screen-on flag lives on the Activity's window, so that is what this holds. */
class AndroidScreenAwake(private val window: Window) : ScreenAwake {
    override fun keepAwake(on: Boolean) =
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
