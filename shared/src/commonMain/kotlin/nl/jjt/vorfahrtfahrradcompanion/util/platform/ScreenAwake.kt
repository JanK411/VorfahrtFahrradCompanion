package nl.jjt.vorfahrtfahrradcompanion.util.platform

/** Holds the display awake. Behind an interface because the flag belongs to a platform window. */
interface ScreenAwake {
    fun keepAwake(on: Boolean)
}
