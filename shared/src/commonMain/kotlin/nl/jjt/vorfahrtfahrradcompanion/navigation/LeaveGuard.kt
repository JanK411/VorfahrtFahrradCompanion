package nl.jjt.vorfahrtfahrradcompanion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import kotlinx.coroutines.launch

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
 * Guards the calling screen against leaving with unsaved work through *every* exit path, so a screen never
 * has to spell the check out twice:
 *  - tab switches and the toolbar arrow ask the gate ([NavigationGate.canLeave]) in App;
 *  - the system/predictive back gesture — which the gate can't see — is caught by the [BackHandler] here.
 *
 * While [blocked] the user is [confirm]ed before leaving; on approval [onLeave] pops the screen. The
 * handler stays disabled while nothing is [blocked] so clean screens keep NavHost's predictive-back
 * animation. Must be composed inside the screen (i.e. the NavHost route content) so its [BackHandler]
 * out-prioritizes NavHost's own back-pop.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeaveGuard(
    blocked: Boolean,
    onLeave: () -> Unit,
    confirm: suspend () -> Boolean,
) {
    val gate = LocalNavigationGate.current
    // Registered once, so read the live values rather than the snapshot captured at registration.
    val currentBlocked by rememberUpdatedState(blocked)
    val currentConfirm by rememberUpdatedState(confirm)
    DisposableEffect(gate) {
        onDispose(gate.register { !currentBlocked || currentConfirm() })
    }

    val scope = rememberCoroutineScope()
    BackHandler(enabled = blocked) {
        scope.launch { if (currentConfirm()) onLeave() }
    }
}
