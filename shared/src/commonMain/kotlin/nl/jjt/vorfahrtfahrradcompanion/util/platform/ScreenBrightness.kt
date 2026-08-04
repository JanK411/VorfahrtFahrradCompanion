package nl.jjt.vorfahrtfahrradcompanion.util.platform

/**
 * The display's brightness. Behind an interface because, like the keep-awake flag, it is a property
 * of a platform window.
 */
interface ScreenBrightness {
    /** Turns the display down to [level], from 0 to 1 — or back to the device's own setting at null. */
    fun set(level: Float?)
}
