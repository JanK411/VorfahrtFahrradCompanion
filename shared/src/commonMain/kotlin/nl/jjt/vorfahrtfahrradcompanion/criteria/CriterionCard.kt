package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.withTimeoutOrNull
import nl.jjt.vorfahrtfahrradcompanion.ui.Spotlight
import kotlin.math.abs

/** How far along a criterion is in the current segment. This drives the whole look of its card. */
internal enum class Review { CONFIRMED, CARRIED, OPEN }

internal fun CriteriaUiState.Ready.reviewOf(criterion: Criterion): Review = when {
    selections[criterion].isEmpty() -> Review.OPEN
    criterion.id in reviewed -> Review.CONFIRMED
    else -> Review.CARRIED
}

/** Big enough to hit on a bumpy road without looking for long. */
private val ValueButtonHeight = 72.dp
private val RowHeight = 64.dp

/** The one precise target on a folded card; everything around it opens the criterion instead. */
private val ApproveButtonSize = 64.dp

/**
 * One criterion. Collapsed it is a single line — label, values, and whether they still need approving —
 * so a filled-in catalogue stays scannable; expanded it is a grid of buttons sized for a mounted phone.
 *
 * Renders any criterion: [CriterionKind] only reaches the tap reducer and the "Next" button, never the
 * layout, which is what lets this screen render a catalogue it has never seen.
 */
@Composable
internal fun CriterionCard(
    criterion: Criterion,
    selected: Set<String>,
    review: Review,
    expanded: Boolean,
    onTapValue: (String) -> Unit,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onNext: () -> Unit,
    onSplit: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                expanded -> scheme.surface
                review == Review.CONFIRMED -> scheme.secondaryContainer
                else -> scheme.surfaceVariant
            },
        ),
        // The amber outline is this app's "needs your attention" mark, as on the "earlier" buttons.
        border = when {
            expanded -> BorderStroke(2.dp, scheme.primary)
            review == Review.CARRIED -> BorderStroke(1.dp, scheme.tertiary)
            else -> null
        },
    ) {
        if (expanded) {
            ExpandedCriterion(criterion, selected, review, onTapValue, onNext)
            return@Card
        }

        // Folded, the card is in two parts: everything the rider reads, which opens on a tap and
        // lets a drag through to scroll the list, and the strip down the right-hand edge, which is
        // where the value menu lives and where a drag belongs to the menu instead.
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.weight(1f).clickable(onClick = onOpen)) {
                if (review == Review.CARRIED) {
                    CarriedCriterion(criterion, selected, onApprove)
                } else {
                    CollapsedCriterion(criterion, selected, confirmed = review == Review.CONFIRMED)
                }
            }

            SplitHandle(criterion, selected, onOpen = onOpen, onSplit = onSplit)
        }
    }
}

/** Wide enough to be found and held by a thumb that is not looking for it. */
private val HandleWidth = 56.dp

/**
 * The strip down the right-hand edge of a folded card, and the only place its value menu opens.
 *
 * A hold anywhere on the card used to do it, which put the gesture in a fight with the list it sits
 * in: a thumb that drifted while pressing scrolled instead, and a thumb that meant to scroll opened
 * a menu. Here the two are settled by where the finger lands. This strip claims the touch the moment
 * it arrives — consumed before the list ever sees it, so a drag started here cannot scroll — and
 * everything to its left is left alone to scroll as it always did.
 */
@Composable
private fun SplitHandle(
    criterion: Criterion,
    selected: Set<String>,
    onOpen: () -> Unit,
    onSplit: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val window = LocalWindowInfo.current.containerSize
    val slack = with(LocalDensity.current) { PickSlack.toPx() }

    // Where the strip sits in the window, since the menu is laid over the window while the thumb is
    // reported against the strip.
    var handleTop by remember { mutableFloatStateOf(0f) }

    var picking by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<String?>(null) }

    // The gesture outlives the composition it started in, so what it does is read through these.
    val currentOpen by rememberUpdatedState(onOpen)
    val currentSplit by rememberUpdatedState(onSplit)

    Box(
        modifier = Modifier.fillMaxHeight().width(HandleWidth)
            .onGloballyPositioned { handleTop = it.positionInWindow().y }
            .pointerInput(criterion) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()

                    // Held down and going nowhere else: the list cannot have this one.
                    var lifted = false
                    var moved = false
                    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes
                                .firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (abs(change.position.y - down.position.y) > slack) moved = true
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                        }
                    } == null

                    // Let go before the hold registers and it is an ordinary tap on the card —
                    // unless the finger travelled, which was a swipe that came up short.
                    if (lifted) {
                        if (!moved) currentOpen()
                        return@awaitEachGesture
                    }
                    if (!held) return@awaitEachGesture

                    // The heavier buzz that says a hold has taken, as on the recorder buttons.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    picking = true

                    val from = handleTop + down.position.y
                    while (true) {
                        val change = awaitPointerEvent().changes
                            .firstOrNull { it.id == down.id } ?: break
                        val at = handleTop + change.position.y
                        // Nothing is picked until the thumb has actually gone somewhere, so a hold
                        // the rider thinks better of ends by lifting off where it started.
                        val slid =
                            if (abs(at - from) < slack) null else criterion.valueUnder(at, window.height)
                        if (slid != choice) {
                            choice = slid
                            if (slid != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        change.consume()
                        if (!change.pressed) break
                    }

                    picking = false
                    choice?.let(currentSplit)
                    choice = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(
            Modifier.align(Alignment.CenterStart).padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = "Hold to change this from here on",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (picking) {
            Popup(FullWindow) { Spotlight(lit = true) { ValuePicker(criterion, selected, choice) } }
        }
    }
}

/** How far the thumb has to travel before the hold counts as having picked anything. */
private val PickSlack = 24.dp

private val PickerCardGap = 8.dp

/**
 * Which value the thumb is over: the menu is the whole window, split evenly, so the only aim asked
 * for is roughly how far up or down to go. There is nowhere on the screen that is not a value —
 * [PickSlack] is what keeps a hold the rider thinks better of from picking the one under their thumb.
 */
private fun Criterion.valueUnder(y: Float, windowHeight: Int): String? {
    if (windowHeight <= 0) return null
    val band = windowHeight.toFloat() / values.size
    return values.getOrNull((y / band).toInt().coerceIn(values.indices))
}

/** Lays the menu over the whole window; the thumb is somewhere on it wherever the card was. */
private object FullWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset.Zero
}

/**
 * The values of one criterion, filling the screen: hold a folded card, slide onto the one that is
 * true from here on, and let go — which ends the segment and opens the next one with that one
 * change. The value the segment already carries is marked, so a rider can see what they are leaving.
 */
@Composable
private fun ValuePicker(criterion: Criterion, selected: Set<String>, choice: String?) = Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(PickerCardGap),
) {
    criterion.values.forEach { value ->
        PickerOption(
            title = value,
            subtitle = if (value in selected) criterion.label() + " now" else null,
            selected = value == choice,
            selectedColor = MaterialTheme.colorScheme.primary,
            selectedContentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A criterion still holding the previous segment's answer. It stays folded — the rider is reviewing what
 * is already there, not filling anything in — and splits into the only two things worth doing to it: the
 * approve button, and everything else, which opens it up to pick something else.
 *
 * The pencil is a hint rather than a button: it sits in the card's own tap area, so hitting it works
 * regardless, and it costs a rider nothing to miss.
 */
@Composable
private fun CarriedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    onApprove: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CriterionSummary(criterion, selected, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))

    Icon(
        Icons.Filled.Edit,
        contentDescription = "Change",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledIconButton(onApprove, Modifier.size(ApproveButtonSize)) {
        Icon(Icons.Filled.Check, contentDescription = "Approve", modifier = Modifier.size(32.dp))
    }
}

/** The label and the values, the two lines every folded card leads with. */
@Composable
private fun CriterionSummary(
    criterion: Criterion,
    selected: Set<String>,
    valueColor: Color,
    modifier: Modifier = Modifier,
) = Column(modifier, Arrangement.spacedBy(2.dp)) {
    Text(
        criterion.label(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        selected.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "—",
        style = MaterialTheme.typography.titleLarge,
        color = valueColor,
    )
}

/**
 * A criterion opened up to be answered: every value as a full-width button, one under the other, sized
 * for a mounted phone. One column rather than two, so a value is read and hit without aiming sideways,
 * and so a long label has the whole width to itself instead of wrapping in half of it.
 *
 * Shared with the end-of-segment question, so a value changed on the way out is picked the same way as
 * one on the list.
 */
@Composable
internal fun ExpandedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    review: Review,
    onTapValue: (String) -> Unit,
    onNext: () -> Unit,
) = Column(
    modifier = Modifier.padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(
            criterion.label(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (criterion.kind == CriterionKind.SINGLE) "pick one" else "pick any",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        criterion.values.forEach { value ->
            ValueButton(
                label = value,
                state = when {
                    value !in selected -> Review.OPEN
                    review == Review.CARRIED -> Review.CARRIED
                    else -> Review.CONFIRMED
                },
                onClick = { onTapValue(value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // A multi-choice criterion has no natural last tap, so it also gets a way to say "done here".
    // Nothing about it may read as one more value: a rule cuts it off from the grid, and where a value
    // is a squared-off half-width button, this is a full-width filled pill carrying an arrow.
    if (criterion.kind == CriterionKind.MULTI) {
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Text("Done — next", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CollapsedCriterion(criterion: Criterion, selected: Set<String>, confirmed: Boolean) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CriterionSummary(
        criterion,
        selected,
        valueColor = if (confirmed) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )

    if (confirmed) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "approved",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One value. Filled means confirmed for this segment, greyed-out means carried over and still waiting for
 * a nod, outlined means not chosen.
 */
@Composable
private fun ValueButton(
    label: String,
    state: Review,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chosen values carry a check as well as a fill, so the state survives sunlight and a glance.
    val content = @Composable {
        if (state != Review.OPEN) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
    val sized = modifier.heightIn(min = ValueButtonHeight)
    val padding = ButtonDefaults.ContentPadding

    when (state) {
        Review.CONFIRMED -> Button(onClick, sized, contentPadding = padding) { content() }

        Review.CARRIED -> FilledTonalButton(
            onClick,
            sized,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) { content() }

        // A two-pixel edge rather than the default hairline: in sunlight a thin outline is the first
        // thing to disappear, and an unchosen value has nothing else to show it is a button at all.
        Review.OPEN -> OutlinedButton(
            onClick,
            sized,
            contentPadding = padding,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) { content() }
    }
}
