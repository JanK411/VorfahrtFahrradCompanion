package nl.jjt.vorfahrtfahrradcompanion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

/** How long the screen goes untouched before it dims. */
private val DimAfter = 20.seconds

/** How far down it goes: dark enough to be worth doing, light enough to still show where things are. */
private const val DimmedLevel = 0.1f

/**
 * The display's brightness. Behind an interface because, like the keep-awake flag, it is a property
 * of a platform window.
 */
interface ScreenBrightness {
    /** Turns the display down to [level], from 0 to 1 — or back to the device's own setting at null. */
    fun set(level: Float?)
}

/**
 * Turns the display down after [DimAfter] without a touch, and back up on the next one.
 *
 * A segment can run for minutes with nothing to answer, and the screen is held awake throughout —
 * so it is held awake dim. It is never put out: the rider gets it back with a touch anywhere rather
 * than a power button and an unlock, neither of which is on offer at twenty kilometres an hour.
 *
 * That touch does nothing else. It is swallowed here, in the pass that runs before anything below
 * sees it, because a rider reaching for a dark screen is reaching for the screen and not for
 * whatever happens to lie under their thumb — approving a criterion or ending a segment by accident
 * costs far more than the second tap this asks for.
 */
@Composable
fun DimWhenIdle(
    enabled: Boolean,
    brightness: ScreenBrightness = koinInject(),
    content: @Composable () -> Unit,
) {
    // Counted rather than timestamped, and read only inside the effect below: a touch is not worth
    // a recomposition of everything under here, and this way it does not cost one.
    var touches by remember { mutableIntStateOf(0) }
    var dimmed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        dimmed = false
        if (!enabled) return@LaunchedEffect
        // collectLatest, so every touch throws away the wait that was running and starts another.
        snapshotFlow { touches }.collectLatest {
            dimmed = false
            delay(DimAfter)
            dimmed = true
        }
    }

    DisposableEffect(brightness, dimmed) {
        brightness.set(if (dimmed) DimmedLevel else null)
        onDispose { brightness.set(null) }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(dimmed) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)

                    // Read before the touch is counted, since counting it is what undims the screen.
                    val waking = dimmed

                    // Presses and lifts only: a slide would otherwise restart the wait per pixel.
                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) {
                        touches++
                    }

                    if (waking) event.changes.forEach { it.consume() }
                }
            }
        },
    ) {
        content()
    }
}
