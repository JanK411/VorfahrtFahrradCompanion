package nl.jjt.vorfahrtfahrradcompanion.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import nl.jjt.vorfahrtfahrradcompanion.util.platform.ScreenAwake
import org.koin.compose.koinInject

/** Keeps the display on while [enabled] and this is composed, and releases it on the way out. */
@Composable
fun KeepScreenAwake(enabled: Boolean, screen: ScreenAwake = koinInject()) {
    DisposableEffect(screen, enabled) {
        screen.keepAwake(enabled)
        onDispose { screen.keepAwake(false) }
    }
}
