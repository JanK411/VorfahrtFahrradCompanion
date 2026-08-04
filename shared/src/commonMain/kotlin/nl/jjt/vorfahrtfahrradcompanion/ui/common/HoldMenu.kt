package nl.jjt.vorfahrtfahrradcompanion.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A press that says one thing tapped and another held.
 *
 * Released before the platform's long-press timeout it is an [onTap]. Held past it, [onHold] runs and
 * every move afterwards reports the finger's position within this element to [onSlide], until it comes
 * off and [onRelease] closes the gesture. A null [onHold] makes the whole thing a plain button: a slow
 * press is still a tap.
 *
 * The press is claimed the moment it lands, so a gesture started here belongs to it whatever it does
 * next — which is what lets a hold live on a target inside a scrolling list. Where it does, give a
 * [tapSlack]: past that much drift the press was a swipe that came up short rather than a tap, and
 * nothing should happen. [onPressedChange] tracks the finger being down at all, for a target that
 * shows it.
 *
 * This is how every boundary in this app that cannot afford a dialog is marked: the rider holds, the
 * answers fill the screen above their thumb, and they slide onto one and let go, in one movement,
 * without looking. Letting go without having gone anywhere is for the caller to read as "never mind".
 *
 * A gesture outlives the composition it started in, so what the press does is read through
 * [rememberUpdatedState] rather than captured: a restarted `pointerInput` would drop a slide
 * half-made, and this screen recomposes once a second while a segment is running. [key] is what the
 * gesture genuinely belongs to — the criterion, the button — and nothing else restarts it.
 */
@Composable
fun Modifier.holdAndSlide(
    key: Any?,
    enabled: Boolean = true,
    tapSlack: Dp? = null,
    onPressedChange: (Boolean) -> Unit = {},
    onTap: () -> Unit,
    onHold: ((from: Offset) -> Unit)? = null,
    onSlide: (Offset) -> Unit = {},
    onRelease: () -> Unit = {},
): Modifier {
    val currentTap by rememberUpdatedState(onTap)
    val currentHold by rememberUpdatedState(onHold)
    val currentSlide by rememberUpdatedState(onSlide)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentPressed by rememberUpdatedState(onPressedChange)

    // Whether there is anything behind a hold is part of what the gesture *is*, so it restarts on
    // that — but on the fact of it, never on the identity of the lambda carrying it.
    val holdable = onHold != null

    return pointerInput(key, enabled, holdable, tapSlack) {
        if (!enabled) return@pointerInput
        val slack = tapSlack?.toPx()

        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            currentPressed(true)

            try {
                var strayed = false
                val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    follow(down) { at ->
                        if (slack != null && (at - down.position).getDistance() > slack) strayed = true
                    }
                }

                when (lifted) {
                    // Let go before the hold registered: an ordinary tap, unless the finger travelled.
                    true -> {
                        if (!strayed) currentTap()
                        return@awaitEachGesture
                    }
                    // The pointer went elsewhere; nothing was meant by it.
                    false -> return@awaitEachGesture
                    // Still down, so the hold has taken.
                    null -> Unit
                }

                if (!holdable) {
                    if (follow(down)) currentTap()
                    return@awaitEachGesture
                }

                currentHold?.invoke(down.position)
                follow(down, currentSlide)
                currentRelease()
            } finally {
                currentPressed(false)
            }
        }
    }
}

/**
 * Follows [down] until the finger comes off — true — or the pointer is lost to something else, which
 * is false. Every move on the way is reported to [onMove] and consumed, so nothing underneath sees it.
 */
private suspend fun AwaitPointerEventScope.follow(
    down: PointerInputChange,
    onMove: (Offset) -> Unit = {},
): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return false
        onMove(change.position)
        change.consume()
        if (!change.pressed) return true
    }
}

/**
 * Lays a hold menu over the window from its top-left corner. The menu sizes itself; anchoring it to
 * the button that opened it would put it wherever that button happens to be, which is the one thing
 * it must not depend on.
 */
object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset.Zero
}

/**
 * One card in a hold menu. Big, plain, and lit only when the thumb is on it, because it is read at a
 * glance by someone who is also steering.
 */
@Composable
fun HoldMenuOption(
    title: String,
    selected: Boolean,
    selectedColor: Color,
    selectedContentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
    shadowElevation = if (selected) 8.dp else 2.dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
