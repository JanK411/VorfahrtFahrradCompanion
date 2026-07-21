package nl.jjt.vorfahrtfahrradcompanion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred

/**
 * Turns a dialog into a suspending question: the returned function shows [dialog] and suspends until it
 * calls back with the answer. Meant for [LeaveGuard], where the guard must decide before navigation
 * continues — `LeaveGuard { !hasUnsavedChanges || confirm() }`.
 *
 * An unanswered question is completed with `false` on dispose, so no caller hangs.
 */
@Composable
fun rememberConfirmPrompt(dialog: @Composable (answer: (Boolean) -> Unit) -> Unit): suspend () -> Boolean {
    var pending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }

    DisposableEffect(Unit) {
        onDispose { pending?.complete(false) }
    }

    pending?.let { question ->
        dialog { answer ->
            pending = null
            question.complete(answer)
        }
    }

    return remember { { CompletableDeferred<Boolean>().also { pending = it }.await() } }
}
