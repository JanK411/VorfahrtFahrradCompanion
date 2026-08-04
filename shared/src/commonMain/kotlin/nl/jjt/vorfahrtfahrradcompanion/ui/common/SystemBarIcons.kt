package nl.jjt.vorfahrtfahrradcompanion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import nl.jjt.vorfahrtfahrradcompanion.platform.SystemBars
import org.koin.compose.koinInject

/** Keeps the bar icons readable against whichever colour scheme is up. */
@Composable
fun SystemBarIcons(dark: Boolean, bars: SystemBars = koinInject()) {
    LaunchedEffect(bars, dark) { bars.iconsFor(dark) }
}
