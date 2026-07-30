package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.koinInject

/** Holds the display awake. Behind an interface because the flag belongs to a platform window. */
interface ScreenAwake {
    fun keepAwake(on: Boolean)
}

/** Keeps the display on while [enabled] and this is composed, and releases it on the way out. */
@Composable
fun KeepScreenAwake(enabled: Boolean, screen: ScreenAwake = koinInject()) {
    DisposableEffect(screen, enabled) {
        screen.keepAwake(enabled)
        onDispose { screen.keepAwake(false) }
    }
}
