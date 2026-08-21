package nl.jjt.vorfahrtfahrradcompanion.ui.criteria.jan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.EndTiming
import nl.jjt.vorfahrtfahrradcompanion.ui.common.HoldMenuOption
import nl.jjt.vorfahrtfahrradcompanion.ui.common.WindowOrigin
import nl.jjt.vorfahrtfahrradcompanion.ui.common.holdAndSlide
import nl.jjt.vorfahrtfahrradcompanion.ui.theme.Spotlight

/** Keeps both buttons the same height when one of them wraps onto a second line. */
@Composable
internal fun ButtonRow(content: @Composable RowScope.() -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
)

private val ACTION_BUTTON_HEIGHT = 72.dp

/** How far above the button the stack of answers starts, and how far in from the sides it sits. */
private val MENU_GAP = 12.dp

/** Bottom to top, so the further the thumb travels, the later the press was. */
private val TIMING_CARDS = EndTiming.entries.reversed()

/**
 * A boundary marker. A tap marks the boundary here and now; where there is an [onPick], holding it says
 * something about the moment that has passed — the correction a rider reaches for after missing it, on
 * the same button rather than beside it.
 *
 * Holding answers the question a tap would raise as a dialog: how well the press caught the boundary
 * decides whether the segment is worth keeping, so the hold fills the screen above the thumb with the
 * three answers and the rider slides straight up onto one and lets go, in one movement, the way a
 * phone's quick launch works. Lifting off without having gone anywhere leaves the segment alone.
 *
 * Built out of a Surface because a Button has room for neither a long press nor what follows it.
 */
@Composable
internal fun RowScope.RecorderButton(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    icon: ImageVector? = null,
    onPick: ((EndTiming) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val gapPx = with(LocalDensity.current) { MENU_GAP.toPx() }

    // Where the button's top edge sits in the window — the stack fills everything above it, and the
    // slide is measured against exactly that, so the card that lights up is the one under the thumb.
    var buttonTop by remember { mutableFloatStateOf(0f) }
    val stackPx = buttonTop - gapPx

    var picking by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<EndTiming?>(null) }

    Surface(
        modifier = Modifier.weight(1f).heightIn(min = ACTION_BUTTON_HEIGHT).fillMaxHeight()
            .onGloballyPositioned { buttonTop = it.positionInWindow().y }
            .holdAndSlide(
                key = label,
                enabled = enabled,
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTap()
                },
                onHold = onPick?.let {
                    { _ ->
                        // The heavier buzz, so the two gestures feel apart without a look at the screen.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        picking = true
                    }
                },
                onSlide = { position ->
                    // Measured off the button's top edge, which is where the stack is anchored — not
                    // off the press, which lands anywhere on the button.
                    val slid = cardUnder(-position.y, stackPx, gapPx)
                    if (slid != choice) {
                        choice = slid
                        // A tick as the thumb crosses onto an answer, since it covers the screen.
                        if (slid != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                },
                onRelease = {
                    picking = false
                    choice?.let { onPick?.invoke(it) }
                    choice = null
                },
            ),
        shape = ButtonDefaults.shape,
        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }

            if (picking) {
                Popup(WindowOrigin) {
                    // The screen is asking which of three it is, and nothing else is: lit, like a
                    // criterion under the thumb, so the answer can be read off it in full sun.
                    Spotlight(lit = true) {
                        HowLatePicker(choice, with(LocalDensity.current) { stackPx.toDp() })
                    }
                }
            }
        }
    }
}

/**
 * Which card the thumb is on, from how far above the button's top edge it has got: the [stack] is the
 * whole screen above the button, split three ways, so the only aim asked for is how far up to slide.
 * Below the stack is no answer at all; past the top one the answer stays on it, since the thumb cannot
 * be anywhere else.
 */
private fun cardUnder(aboveButton: Float, stack: Float, gap: Float): EndTiming? {
    if (stack <= 0f || aboveButton < gap) return null
    val index = ((aboveButton - gap) / (stack / TIMING_CARDS.size)).toInt()
    return TIMING_CARDS.getOrElse(index) { TIMING_CARDS.last() }
}

/**
 * The three answers, filling the screen above the thumb that is still holding the button down: a third
 * of it each, so none of them can be missed by a thumb that slid roughly the right distance.
 */
@Composable
private fun HowLatePicker(choice: EndTiming?, height: Dp) = Column(
    modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = MENU_GAP),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    TIMING_CARDS.forEach { timing ->
        HoldMenuOption(
            title = timing.title,
            icon = timing.icon,
            selected = timing == choice,
            selectedColor = timing.color,
            selectedContentColor = timing.onColor,
            modifier = Modifier.weight(1f),
        )
    }
}
