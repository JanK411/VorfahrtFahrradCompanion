package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject

/**
 * The status- and navigation-bar icons, which the system draws over what the app paints. Behind an
 * interface because they belong to a platform window.
 */
interface SystemBars {
    /** Draws the icons light or dark, for bars standing on a [dark] background or a light one. */
    fun iconsFor(dark: Boolean)
}

/** Keeps the bar icons readable against whichever colour scheme is up. */
@Composable
fun SystemBarIcons(dark: Boolean, bars: SystemBars = koinInject()) {
    LaunchedEffect(bars, dark) { bars.iconsFor(dark) }
}
