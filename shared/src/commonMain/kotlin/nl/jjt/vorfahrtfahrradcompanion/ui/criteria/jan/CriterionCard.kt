package nl.jjt.vorfahrtfahrradcompanion.ui.criteria.jan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.ui.common.HoldMenuOption
import nl.jjt.vorfahrtfahrradcompanion.ui.common.WindowOrigin
import nl.jjt.vorfahrtfahrradcompanion.ui.common.holdAndSlide
import nl.jjt.vorfahrtfahrradcompanion.ui.criteria.label
import nl.jjt.vorfahrtfahrradcompanion.ui.theme.Spotlight
import kotlin.math.abs

/** How far along a criterion is in the current segment. This drives the whole look of its card. */
internal enum class CriterionState {
    /** Nothing chosen — the rider has this one still to answer. */
    OPEN,

    /** Holding the previous segment's answer, which is a suggestion until it is approved. */
    CARRIED_OVER,

    /** Stood by for this segment, so it is what the segment would be stored with. */
    APPROVED,
}

internal fun JanCriteriaUiState.Ready.stateOf(criterion: Criterion): CriterionState = when {
    answers[criterion].isEmpty() -> CriterionState.OPEN
    criterion in approved -> CriterionState.APPROVED
    else -> CriterionState.CARRIED_OVER
}

/** Big enough to hit on a bumpy road without looking for long. */
private val ValueButtonHeight = 72.dp
internal val CRITERION_ROW_HEIGHT = 64.dp

/** The one precise target on a folded card; everything around it opens the criterion instead. */
private val ApproveButtonSize = 64.dp

/**
 * One criterion. Folded it is a single line — label, values, and whether they still need approving —
 * so a filled-in catalogue stays scannable; expanded it is a column of buttons sized for a mounted phone.
 *
 * Renders any criterion: [CriterionKind] reaches the tap reducer, the "Done" button and whether the
 * card carries a split handle at all — never how it is laid out, which is what lets this screen
 * render a catalogue it has never seen.
 */
@Composable
internal fun CriterionCard(
    criterion: Criterion,
    values: List<CriterionValue>,
    selected: Set<CriterionValue>,
    state: CriterionState,
    expanded: Boolean,
    onTapValue: (CriterionValue) -> Unit,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onDone: () -> Unit,
    onSplit: (CriterionValue) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                expanded -> scheme.surface
                state == CriterionState.APPROVED -> scheme.secondaryContainer
                else -> scheme.surfaceVariant
            },
        ),
        // The amber outline is this app's "needs your attention" mark.
        border = when {
            expanded -> BorderStroke(2.dp, scheme.primary)
            state == CriterionState.CARRIED_OVER -> BorderStroke(1.dp, scheme.tertiary)
            else -> null
        },
    ) {
        if (expanded) {
            ExpandedCriterion(criterion, values, selected, state, onTapValue, onDone)
            return@Card
        }

        // Folded, the card is in two parts: everything the rider reads, which opens on a tap and
        // lets a drag through to scroll the list, and the strip down the right-hand edge, which is
        // where the value menu lives and where a drag belongs to the menu instead.
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.weight(1f)) {
                if (state == CriterionState.CARRIED_OVER) {
                    CarriedOverCriterion(criterion, selected, onOpen, onApprove)
                } else {
                    FoldedCriterion(criterion, selected, state, onOpen)
                }
            }

            // Only on a pick-one card. Sliding onto a value says "this is what it is from here on",
            // which a pick-any criterion has no single value to answer with.
            if (criterion.kind == CriterionKind.SINGLE) {
                SplitHandle(criterion, values, selected, onOpen = onOpen, onSplit = onSplit)
            }
        }
    }
}

/** Wide enough to be found and held by a thumb that is not looking for it. */
private val HANDLE_WIDTH = 56.dp

private val KnobWidth = 34.dp
private val KnobArrow = 20.dp

/** How far the knob grows under a thumb — enough to notice at a glance, not enough to jump. */
private const val PRESSED_SCALE = 1.15f

/** How far the thumb has to travel before the hold counts as having picked anything. */
private val PICK_SLACK = 24.dp

/**
 * What the strip looks like: a raised capsule in the app's own green, carrying an arrow at each end.
 *
 * Three dots would say "there is a menu here, tap it", which is neither what this does nor how it is
 * worked. A knob that stands off the card and points both ways says the two things that matter — it
 * is a thing to take hold of, and it goes up and down — and it swells under the thumb, so a rider
 * who has never used it learns what a hold does by starting one.
 */
@Composable
private fun Knob(pressed: Boolean) {
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "knob")

    Surface(
        modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp).width(KnobWidth).scale(scale)
            .semantics { contentDescription = "Hold and slide to change this from here on" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = if (pressed) 8.dp else 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            // Pulled together into one double-headed arrow: two chevrons, not two buttons.
            verticalArrangement = Arrangement.spacedBy((-9).dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(KnobArrow))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(KnobArrow))
        }
    }
}

/**
 * The strip down the right-hand edge of a folded card, and the only place its value menu opens.
 *
 * A hold anywhere on the card would put the gesture in a fight with the list it sits in: a thumb
 * that drifted while pressing would scroll instead, and a thumb that meant to scroll would open a
 * menu. Here the two are settled by where the finger lands. This strip claims the touch the moment
 * it arrives — consumed before the list ever sees it, so a drag started here cannot scroll — and
 * everything to its left is left alone to scroll as it always did.
 */
@Composable
private fun SplitHandle(
    criterion: Criterion,
    values: List<CriterionValue>,
    selected: Set<CriterionValue>,
    onOpen: () -> Unit,
    onSplit: (CriterionValue) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val window = LocalWindowInfo.current.containerSize
    val slack = with(LocalDensity.current) { PICK_SLACK.toPx() }

    // Where the strip sits in the window, since the menu is laid over the window while the thumb is
    // reported against the strip.
    var handleTop by remember { mutableFloatStateOf(0f) }
    var from by remember { mutableFloatStateOf(0f) }

    var picking by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<CriterionValue?>(null) }

    // A thumb resting on the knob makes it grow under itself, which is the whole answer to "is
    // something happening yet?" during the half-second before the hold takes.
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxHeight().width(HANDLE_WIDTH)
            .onGloballyPositioned { handleTop = it.positionInWindow().y }
            .holdAndSlide(
                key = criterion,
                tapSlack = PICK_SLACK,
                onPressedChange = { pressed = it },
                onTap = onOpen,
                onHold = { at ->
                    // The heavier buzz that says a hold has taken, as on the recorder buttons.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    from = handleTop + at.y
                    picking = true
                },
                onSlide = { at ->
                    val y = handleTop + at.y
                    // Nothing is picked until the thumb has actually gone somewhere, so a hold the
                    // rider thinks better of ends by lifting off where it started.
                    val slid = if (abs(y - from) < slack) null else values.valueUnder(y, window.height)
                    if (slid != choice) {
                        choice = slid
                        if (slid != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                },
                onRelease = {
                    picking = false
                    choice?.let(onSplit)
                    choice = null
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Knob(pressed)

        if (picking) {
            Popup(WindowOrigin) {
                Spotlight(lit = true) { ValuePicker(criterion, values, selected, choice) }
            }
        }
    }
}

/**
 * Which value the thumb is over: the menu is the whole window, split evenly, so the only aim asked
 * for is roughly how far up or down to go. There is nowhere on the screen that is not a value —
 * [PICK_SLACK] is what keeps a hold the rider thinks better of from picking the one under their thumb.
 */
private fun List<CriterionValue>.valueUnder(y: Float, windowHeight: Int): CriterionValue? {
    if (windowHeight <= 0) return null
    val band = windowHeight.toFloat() / size
    return getOrNull((y / band).toInt().coerceIn(indices))
}

/**
 * The values of one criterion, filling the screen: hold a folded card's knob, slide onto the one
 * that is true from here on, and let go — which ends the segment and opens the next one with that
 * one change. The value the segment already carries is marked, so a rider can see what they leave.
 */
@Composable
private fun ValuePicker(
    criterion: Criterion,
    values: List<CriterionValue>,
    selected: Set<CriterionValue>,
    choice: CriterionValue?,
) = Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    values.forEach { value ->
        HoldMenuOption(
            title = value.id,
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
private fun CarriedOverCriterion(
    criterion: Criterion,
    selected: Set<CriterionValue>,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = CRITERION_ROW_HEIGHT)
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 8.dp),
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
        Icon(
            Icons.Filled.Check,
            contentDescription = "Approve ${criterion.label()}",
            modifier = Modifier.size(32.dp),
        )
    }
}

/** A criterion the rider is done with, or has not started: one line, tapped to open it up again. */
@Composable
private fun FoldedCriterion(
    criterion: Criterion,
    selected: Set<CriterionValue>,
    state: CriterionState,
    onOpen: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = CRITERION_ROW_HEIGHT)
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val approved = state == CriterionState.APPROVED

    CriterionSummary(
        criterion,
        selected,
        valueColor = if (approved) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )

    if (approved) {
        Icon(Icons.Filled.Check, contentDescription = "Approved", tint = MaterialTheme.colorScheme.primary)
    }
}

/** The label and the values, the two lines every folded card leads with. */
@Composable
private fun CriterionSummary(
    criterion: Criterion,
    selected: Set<CriterionValue>,
    valueColor: Color,
    modifier: Modifier = Modifier,
) = Column(modifier, Arrangement.spacedBy(2.dp)) {
    Text(
        criterion.label(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        selected.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.id } ?: "—",
        style = MaterialTheme.typography.titleLarge,
        color = valueColor,
    )
}

/**
 * A criterion opened up to be answered: every value as a full-width button, one under the other, sized
 * for a mounted phone. One column rather than two, so a value is read and hit without aiming sideways,
 * and so a long label has the whole width to itself instead of wrapping in half of it.
 */
@Composable
internal fun ExpandedCriterion(
    criterion: Criterion,
    values: List<CriterionValue>,
    selected: Set<CriterionValue>,
    state: CriterionState,
    onTapValue: (CriterionValue) -> Unit,
    onDone: () -> Unit,
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
        values.forEach { value ->
            ValueButton(
                label = value.id,
                state = when {
                    value !in selected -> CriterionState.OPEN
                    state == CriterionState.CARRIED_OVER -> CriterionState.CARRIED_OVER
                    else -> CriterionState.APPROVED
                },
                onClick = { onTapValue(value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // A pick-any criterion has no natural last tap, so it also gets a way to say "done here". Nothing
    // about it may read as one more value: a rule cuts it off from the values, and where a value is a
    // squared-off outlined button, this is a filled pill carrying an arrow.
    if (criterion.kind == CriterionKind.MULTI) {
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = CRITERION_ROW_HEIGHT),
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

/**
 * One value. Filled means approved for this segment, greyed-out means carried over and still waiting for
 * a nod, outlined means not chosen.
 */
@Composable
private fun ValueButton(
    label: String,
    state: CriterionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chosen values carry a check as well as a fill, so the state survives sunlight and a glance.
    val content = @Composable {
        if (state != CriterionState.OPEN) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
    val sized = modifier.heightIn(min = ValueButtonHeight)
    val padding = ButtonDefaults.ContentPadding

    when (state) {
        CriterionState.APPROVED -> Button(onClick, sized, contentPadding = padding) { content() }

        CriterionState.CARRIED_OVER -> FilledTonalButton(
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
        CriterionState.OPEN -> OutlinedButton(
            onClick,
            sized,
            contentPadding = padding,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) { content() }
    }
}
