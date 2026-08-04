package nl.jjt.vorfahrtfahrradcompanion.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import nl.jjt.vorfahrtfahrradcompanion.platform.ScreenBrightness
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

/** How long the screen goes untouched before it dims. */
private val DIM_AFTER = 20.seconds

/**
 * How far down the backlight goes. Not as far as it would have to on its own, because the veil below
 * darkens the screen by as much again: together they land where the backlight alone used to, and the
 * shorter the drop, the shorter the climb back that the platform ramps at its own pace.
 */
private const val DIMMED_LEVEL = 0.25f

/** How far the screen is veiled on top of that, which is the part of the dimming this app owns. */
private const val SCRIM_ALPHA = 0.7f

/** How long the veil takes to draw: slow enough to read as the screen resting, not as a fault. */
private const val FADE_MILLIS = 2000

/**
 * Turns the display down after [DIM_AFTER] without a touch, and back up on the next one.
 *
 * A segment can run for minutes with nothing to answer, and the screen is held awake throughout —
 * so it is held awake dim. It is never put out: the rider gets it back with a touch anywhere rather
 * than a power button and an unlock, neither of which is on offer at twenty kilometres an hour.
 *
 * That touch does nothing else. It is swallowed here, in the pass that runs before anything below
 * sees it, because a rider reaching for a dark screen is reaching for the screen and not for
 * whatever happens to lie under their thumb — approving a criterion or ending a segment by accident
 * costs far more than the second tap this asks for.
 *
 * Most of what the rider sees is a veil this draws itself rather than the backlight, and for one
 * reason: the platform ramps the backlight at its own pace, which is a pleasant couple of seconds on
 * the way down and the same couple of seconds on the way back — and coming back is the half that has
 * to be immediate. A veil is drawn on the next frame, so waking is one frame, whatever the backlight
 * is doing behind it.
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
            delay(DIM_AFTER)
            dimmed = true
        }
    }

    DisposableEffect(brightness, dimmed) {
        brightness.set(if (dimmed) DIMMED_LEVEL else null)
        onDispose { brightness.set(null) }
    }

    // Down over a couple of seconds, up in a single frame: a screen fading out is the app resting,
    // a screen taking two seconds to come back is the app in the way.
    val veil by animateFloatAsState(
        targetValue = if (dimmed) SCRIM_ALPHA else 0f,
        animationSpec = if (dimmed) tween(FADE_MILLIS, easing = LinearEasing) else snap(),
        label = "dim",
    )

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

        // Nothing but a colour: it carries no pointer input, so it is not a hit target and the
        // touch that clears it goes to the interceptor above like any other.
        if (veil > 0f) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = veil)))
        }
    }
}
