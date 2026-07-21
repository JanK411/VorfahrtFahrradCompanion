package nl.jjt.vorfahrtfahrradcompanion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/** Holds the guard of the screen currently in composition; empty means "leaving is always fine". */
@Stable
class NavigationGate {
    private var guard: (suspend () -> Boolean)? = null

    /** Returns a handle that unregisters [g] again; calling it is a no-op once another guard took over. */
    fun register(g: suspend () -> Boolean): () -> Unit {
        guard = g
        return { if (guard === g) guard = null }
    }

    /** Suspends while the current screen asks the user; true when navigation may proceed. */
    suspend fun canLeave(): Boolean = guard?.invoke() ?: true
}

val LocalNavigationGate = staticCompositionLocalOf { NavigationGate() }

/**
 * Registers [canLeave] for as long as the calling screen is composed. [canLeave] is registered once, so it
 * must read live state rather than a captured Compose snapshot value.
 */
@Composable
fun LeaveGuard(canLeave: suspend () -> Boolean) {
    val gate = LocalNavigationGate.current
    DisposableEffect(gate) {
        val unregister = gate.register(canLeave)
        onDispose(unregister)
    }
}
