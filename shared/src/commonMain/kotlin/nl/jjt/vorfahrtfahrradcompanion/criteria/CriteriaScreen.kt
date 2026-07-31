package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import nl.jjt.vorfahrtfahrradcompanion.ui.KeepScreenAwake
import nl.jjt.vorfahrtfahrradcompanion.ui.secondsSince
import org.koin.compose.viewmodel.koinViewModel

/**
 * How long a multi-choice criterion waits after a tap before moving on. Long enough to add a second
 * value, short enough that a rider who is done does not have to reach for the "Next" button.
 */
private const val MultiAdvanceMillis = 1500L

private val ActionButtonHeight = 72.dp

// TODO VF-116: guard an open segment with a LeaveGuard (see ServerConnectionScreen), so navigating away
//  mid-segment asks first instead of relying on the repository quietly holding on to it.
@Composable
fun CriteriaScreen(modifier: Modifier = Modifier) {
    val viewModel: CriteriaViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        CriteriaUiState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }

        is CriteriaUiState.Failed -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::retry) { Text("Retry") }
        }

        is CriteriaUiState.Ready -> Catalogue(
            state = s,
            outcomes = viewModel.outcomes,
            advances = viewModel.advances,
            onTap = viewModel::onTap,
            onConfirm = viewModel::onConfirm,
            onStart = viewModel::start,
            onEnd = viewModel::end,
            onEndAs = viewModel::endAs,
            onAnswerTiming = viewModel::answerTiming,
            onCancelTiming = viewModel::cancelTiming,
            onConfirmEnd = viewModel::confirmEnd,
            onCancelEnd = viewModel::cancelEnd,
            onDiscardSegment = viewModel::discardSegment,
            onClearCarriedOver = viewModel::clearCarriedOver,
            onUndoClear = viewModel::undoClear,
            modifier = modifier,
        )
    }
}

@Composable
private fun Catalogue(
    state: CriteriaUiState.Ready,
    outcomes: Flow<SegmentOutcome>,
    advances: Flow<Criterion>,
    onTap: (Criterion, String) -> Unit,
    onConfirm: (Criterion) -> Unit,
    onStart: (BoundaryKind) -> Unit,
    onEnd: (SegmentAction) -> Unit,
    onEndAs: (SegmentAction, EndTiming) -> Unit,
    onAnswerTiming: (EndTiming) -> Unit,
    onCancelTiming: () -> Unit,
    onConfirmEnd: (Set<String>) -> Unit,
    onCancelEnd: () -> Unit,
    onDiscardSegment: () -> Unit,
    onClearCarriedOver: () -> Unit,
    onUndoClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var discarded by remember { mutableStateOf(false) }
    LaunchedEffect(outcomes) {
        outcomes.collect { outcome ->
            discarded = outcome != SegmentOutcome.SAVED
            snackbarHostState.showSnackbar(
                when (outcome) {
                    SegmentOutcome.SAVED -> "Segment saved"
                    SegmentOutcome.NOTHING_TO_STORE -> "Nothing approved — segment discarded"
                    SegmentOutcome.DISCARDED -> "Segment discarded"
                    SegmentOutcome.TOO_LATE ->
                        "More than ${LateEndGrace.inWholeSeconds} s late — segment discarded"
                },
            )
        }
    }

    // Unlocking a phone in a holder is not an option, so the display stays on while a segment runs.
    KeepScreenAwake(state.segment is Segment.Open)

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // The card the rider is working on. A tap pins it, so it cannot slide away under a thumb mid-answer,
    // and advancing unpins it again.
    var pinned by remember { mutableStateOf<String?>(null) }

    // How far down the list the rider has got. The flow leads from below it and only doubles back once
    // there is nothing left below to lead to.
    var settled by remember { mutableStateOf<Criterion?>(null) }

    // With nothing pinned the screen leads: it expands the criterion to answer next — but only while
    // there is nothing left to review. A segment that inherited the last one's answers stays folded, so
    // the rider approves or changes them one line at a time instead of being dropped into the first one.
    val expanded = state.catalogue.criteria.firstOrNull { it.id == pinned }
        ?: state.leadingAfter(settled).takeIf { state.carriedOver.isEmpty() }

    // A new segment starts at the top. The list keeps its scroll position across an end, and the first
    // open criterion is usually the same one as before, so nothing below would move the view back up.
    LaunchedEffect(state.segment) {
        pinned = null
        settled = null
        listState.scrollToItem(0)
    }

    // The collector below outlives the composition it started in, so it reads the state through this.
    val current by rememberUpdatedState(state)

    // Moving on: bring whatever comes after this one up the list, which is below it until the bottom
    // of the list is reached and whatever was skipped comes back round.
    fun moveOnFrom(criterion: Criterion) {
        val row = current.rowOf(current.leadingAfter(criterion))
        if (row >= 0) scope.launch { listState.animateScrollToItem(row) }
    }

    LaunchedEffect(advances) {
        // collectLatest, so every further tap restarts the wait instead of stacking up advances.
        advances.collectLatest { criterion ->
            if (criterion.kind == CriterionKind.MULTI) delay(MultiAdvanceMillis)
            // Unless the rider has opened something else in the meantime, in which case they lead.
            if (pinned != criterion.id) return@collectLatest
            pinned = null
            settled = criterion

            // While anything else is still up for review nothing expands, so the list has to be moved
            // on from here — the same step approving takes. Otherwise the criterion that expands next
            // brings itself into view.
            if (current.carriedOver.any { it.id != criterion.id }) moveOnFrom(criterion)
        }
    }

    // Answer one at the top and the next one comes to you — no scrolling while riding.
    LaunchedEffect(expanded?.id) {
        val row = state.rowOf(expanded)
        if (row >= 0) listState.animateScrollToItem(row)
    }

    var discarding by remember { mutableStateOf(false) }
    if (discarding) {
        DiscardSegmentDialog(
            onConfirm = {
                discarding = false
                onDiscardSegment()
            },
            onDismiss = { discarding = false },
        )
    }

    state.pendingTiming?.let {
        EndTimingDialog(action = it.action, onAnswer = onAnswerTiming, onDismiss = onCancelTiming)
    }

    state.pendingEnd?.let { pending ->
        EndSegmentDialog(
            // The list the question opened with: editing an answer in it approves that criterion, which
            // would take it out of a list read off the state mid-answer.
            unapproved = pending.asked,
            selections = state.selections,
            reviewed = state.reviewed,
            onEdit = onTap,
            onConfirm = onConfirmEnd,
            onDismiss = onCancelEnd,
        )
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // The criteria describe the stretch being recorded, so they only make sense once one is open.
            if (state.segment is Segment.Open) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The very first row, so that answering anything below scrolls it out of the way for
                    // good — it is a decision about a fresh segment, not one to keep meeting.
                    if (state.carriedOver.isNotEmpty()) {
                        item(key = ClearCarriedOverKey) {
                            ClearCarriedOverButton(carried = state.carriedOver.size) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onClearCarriedOver()
                                discarded = false
                                scope.launch {
                                    val undo = snackbarHostState.showSnackbar(
                                        message = "Cleared — filling in from scratch",
                                        actionLabel = "Undo",
                                    )
                                    if (undo == SnackbarResult.ActionPerformed) onUndoClear()
                                }
                            }
                        }
                    }

                    items(state.catalogue.criteria, key = Criterion::id) { criterion ->
                        CriterionCard(
                            criterion = criterion,
                            selected = state.selections[criterion],
                            review = state.reviewOf(criterion),
                            expanded = criterion.id == expanded?.id,
                            onTapValue = { value ->
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                pinned = criterion.id
                                onTap(criterion, value)
                            },
                            onOpen = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                pinned = criterion.id
                            },
                            onApprove = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onConfirm(criterion)
                                settled = criterion
                                moveOnFrom(criterion)
                            },
                            onNext = {
                                onConfirm(criterion)
                                pinned = null
                            },
                        )
                    }
                }
            } else {
                Text(
                    "Start a segment to describe it.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter)) { data ->
                Snackbar(
                    data,
                    containerColor = if (discarded) MaterialTheme.colorScheme.tertiaryContainer
                    else SnackbarDefaults.color,
                    contentColor = if (discarded) MaterialTheme.colorScheme.onTertiaryContainer
                    else SnackbarDefaults.contentColor,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (state.saveState as? SaveState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            val enabled = state.saveState !is SaveState.InFlight
            when (val segment = state.segment) {
                Segment.Idle -> ButtonRow {
                    RecorderButton(
                        label = "Start",
                        enabled = enabled,
                        onTap = { onStart(BoundaryKind.EXACT) },
                        hold = Hold.Mark { onStart(BoundaryKind.EARLIER) },
                    )
                }

                is Segment.Open -> {
                    Progress(
                        segment = segment,
                        confirmed = state.confirmed.size,
                        carried = state.carriedOver.size,
                        total = state.catalogue.criteria.size,
                        onDiscard = { discarding = true },
                    )

                    ButtonRow {
                        RecorderButton(
                            label = "End",
                            enabled = enabled,
                            onTap = { onEnd(SegmentAction.STOP) },
                            hold = Hold.Pick { onEndAs(SegmentAction.STOP, it) },
                        )
                        RecorderButton(
                            label = "Start next",
                            enabled = enabled,
                            onTap = { onEnd(SegmentAction.START_NEXT) },
                            hold = Hold.Pick { onEndAs(SegmentAction.START_NEXT, it) },
                        )
                    }
                }
            }
        }
    }
}

private const val ClearCarriedOverKey = "clear-carried-over"

/** Where a criterion sits in the list, counting the clear button that leads it while there is one. */
private fun CriteriaUiState.Ready.rowOf(criterion: Criterion?): Int {
    val index = catalogue.criteria.indexOfFirst { it.id == criterion?.id }
    return if (index < 0) -1 else index + if (carriedOver.isEmpty()) 0 else 1
}

/**
 * The way out of a segment that inherited answers describing somewhere else entirely. It leads the list
 * rather than sitting above it, because it is the first thing a rider decides about a new segment — and
 * once they have answered anything below, it scrolls away instead of staying in reach.
 */
@Composable
private fun ClearCarriedOverButton(
    carried: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
) {
    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text("Clear $carried preselected", style = MaterialTheme.typography.titleMedium)
}

/**
 * The question in front of throwing a segment away. Unlike a boundary, nothing about this is
 * time-critical, so it can afford to be asked.
 */
@Composable
private fun DiscardSegmentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Discard this segment?") },
    text = {
        Text(
            "Nothing about the stretch you are recording is stored, and whatever you described it " +
                "with is cleared. The next segment starts from scratch.",
            style = MaterialTheme.typography.bodyMedium,
        )
    },
    confirmButton = {
        Button(
            onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text("Discard") }
    },
    dismissButton = { TextButton(onDismiss) { Text("Keep recording") } },
)

/**
 * How long the open segment has been running, how much of the catalogue it carries, and — because
 * unconfirmed values are not stored — how much of it would be dropped right now.
 */
@Composable
private fun Progress(
    segment: Segment.Open,
    confirmed: Int,
    carried: Int,
    total: Int,
    onDiscard: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    val elapsed = secondsSince(segment.startedAt)
    val startedEarlier = if (segment.startKind == BoundaryKind.EARLIER) " · started earlier" else ""

    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Text(
            "● Recording ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}$startedEarlier",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$confirmed / $total",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onDiscard, Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Discard segment",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (carried > 0) {
        Text(
            "$carried still unapproved — you are asked about them when the segment ends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Keeps both buttons the same height when one of them wraps onto a second line. */
@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
)

private val PickerCardWidth = 260.dp
private val PickerCardHeight = 76.dp
private val PickerCardGap = 8.dp

/** How far above the button the stack of cards starts. */
private val PickerGap = 16.dp

/** Bottom to top, so the further the thumb travels, the later the press was. */
private val TimingCards = listOf(EndTiming.EXACT, EndTiming.JUST_NOW, EndTiming.LONGER)

/** What holding a recorder button down does, once the hold registers. */
private sealed interface Hold {
    /** Marks the boundary as already passed, there and then. */
    data class Mark(val onMark: () -> Unit) : Hold

    /** Stacks the three end answers above the thumb, for the rider to slide onto one and let go. */
    data class Pick(val onPick: (EndTiming) -> Unit) : Hold
}

/**
 * A boundary marker. A tap marks the boundary here and now; holding it says something about the moment
 * that has passed — the correction a rider reaches for after missing it, on the same button rather than
 * beside it.
 *
 * On an end, holding answers the question a tap would raise as a dialog: how well the press caught the
 * boundary decides whether the segment is worth keeping, so the hold stacks the three answers above the
 * thumb and the rider slides straight up onto one and lets go, in one movement, the way a phone's quick
 * launch works. Lifting off without having gone anywhere leaves the segment alone.
 *
 * Built out of a Surface because a Button has room for neither a long press nor what follows it.
 */
@Composable
private fun RowScope.RecorderButton(
    label: String,
    enabled: Boolean,
    onTap: () -> Unit,
    hold: Hold,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // The same geometry the cards are laid out with, so the highlighted one is the one under the thumb.
    val density = LocalDensity.current
    val cardPx = with(density) { PickerCardHeight.toPx() }
    val cardGapPx = with(density) { PickerCardGap.toPx() }
    val stackGapPx = with(density) { PickerGap.toPx() }

    var picking by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<EndTiming?>(null) }

    // The gesture outlives the composition it started in, and a restarted pointerInput would drop a
    // slide half-made, so what the press does is read through this rather than keyed on.
    val currentTap by rememberUpdatedState(onTap)
    val currentHold by rememberUpdatedState(hold)

    Surface(
        modifier = Modifier.weight(1f).heightIn(min = ActionButtonHeight).fillMaxHeight()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()

                    // Released before the hold registered: an ordinary tap, on the boundary itself.
                    if (withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        } != null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentTap()
                        return@awaitEachGesture
                    }

                    // The heavier buzz, so the two boundaries feel apart without a look at the screen.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    when (val held = currentHold) {
                        is Hold.Mark -> {
                            held.onMark()
                            waitForUpOrCancellation()
                        }

                        is Hold.Pick -> {
                            picking = true
                            while (true) {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id } ?: break
                                // Measured off the button's top edge, which is where the stack is
                                // anchored — not off the press, which lands anywhere on the button.
                                val slid = cardUnder(-change.position.y, cardPx, cardGapPx, stackGapPx)
                                if (slid != choice) {
                                    choice = slid
                                    // A tick as the thumb crosses onto an answer, since it covers the
                                    // screen.
                                    if (slid != null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                }
                                change.consume()
                                if (!change.pressed) break
                            }

                            picking = false
                            choice?.let(held.onPick)
                            choice = null
                        }
                    }
                }
            },
        shape = ButtonDefaults.shape,
        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

            if (picking) {
                val gap = with(LocalDensity.current) { PickerGap.roundToPx() }
                Popup(remember(gap) { AboveTheButton(gap) }) { HowLatePicker(choice) }
            }
        }
    }
}

/**
 * Puts the answers across the middle of the window, [gap] above the button. The cards are wider than
 * the half-width button they belong to, so centring on the button would push them off the screen.
 */
private class AboveTheButton(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        x = (windowSize.width - popupContentSize.width) / 2,
        y = anchorBounds.top - popupContentSize.height - gap,
    )
}

/**
 * Which card the thumb is on, from how far above the button's top edge it has got: one full-width card
 * per answer, so the only aim asked for is how far up to slide. Below the stack is no answer at all;
 * past the top one the answer stays on it, since the thumb cannot be anywhere else.
 */
private fun cardUnder(aboveButton: Float, card: Float, gap: Float, stackGap: Float): EndTiming? {
    if (aboveButton < stackGap) return null
    val index = ((aboveButton - stackGap) / (card + gap)).toInt()
    return TimingCards.getOrElse(index) { TimingCards.last() }
}

/** The three answers, stacked above the thumb that is still holding the button down. */
@Composable
private fun HowLatePicker(choice: EndTiming?) = Column(
    verticalArrangement = Arrangement.spacedBy(PickerCardGap),
) {
    PickerOption(
        title = "Longer",
        subtitle = "discard segment",
        selected = choice == EndTiming.LONGER,
        selectedColor = MaterialTheme.colorScheme.error,
        selectedContentColor = MaterialTheme.colorScheme.onError,
    )
    PickerOption(
        title = "~${LateEndGrace.inWholeSeconds} s late",
        subtitle = "end goes back",
        selected = choice == EndTiming.JUST_NOW,
        selectedColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    PickerOption(
        title = "Precisely",
        subtitle = "end at the press",
        selected = choice == EndTiming.EXACT,
        selectedColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun PickerOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    selectedColor: Color,
    selectedContentColor: Color,
) = Surface(
    modifier = Modifier.size(PickerCardWidth, PickerCardHeight),
    shape = MaterialTheme.shapes.large,
    color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
    shadowElevation = if (selected) 8.dp else 2.dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.labelMedium)
    }
}
