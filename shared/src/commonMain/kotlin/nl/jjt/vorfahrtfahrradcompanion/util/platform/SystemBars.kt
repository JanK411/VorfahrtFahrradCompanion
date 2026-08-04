package nl.jjt.vorfahrtfahrradcompanion.util.platform

/**
 * The status- and navigation-bar icons, which the system draws over what the app paints. Behind an
 * interface because they belong to a platform window.
 */
interface SystemBars {
    /** Draws the icons light or dark, for bars standing on a [dark] background or a light one. */
    fun iconsFor(dark: Boolean)
}
