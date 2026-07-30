package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
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
            onConfirmEnd = viewModel::confirmEnd,
            onCancelEnd = viewModel::cancelEnd,
            onDiscardSegment = viewModel::discardSegment,
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
    onEnd: (BoundaryKind, SegmentAction, MissedEnd?) -> Unit,
    onConfirmEnd: (Set<String>) -> Unit,
    onCancelEnd: () -> Unit,
    onDiscardSegment: () -> Unit,
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
                        "More than ${MissedEndGrace.inWholeSeconds} s late — segment discarded"
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

    // How far down the list the rider has got. Everything above it is behind them — answered, or
    // skipped on purpose — so the flow leads from below it and never doubles back.
    var settled by remember { mutableStateOf<Criterion?>(null) }

    // With nothing pinned the screen leads: it expands the criterion to answer next — but only while
    // there is nothing left to review. A segment that inherited the last one's answers stays folded, so
    // the rider approves or changes them one line at a time instead of being dropped into the first one.
    val last = settled
    val leading = if (last == null) state.nextOpen else state.openAfter(last)
    val expanded = state.catalogue.criteria.firstOrNull { it.id == pinned }
        ?: leading.takeIf { state.carriedOver.isEmpty() }

    // A new segment starts at the top. The list keeps its scroll position across an end, and the first
    // open criterion is usually the same one as before, so nothing below would move the view back up.
    LaunchedEffect(state.segment) {
        pinned = null
        settled = null
        listState.scrollToItem(0)
    }

    // The collector below outlives the composition it started in, so it reads the state through this.
    val current by rememberUpdatedState(state)

    // Moving on: bring the criterion below this one that still needs attention up the list. Forward
    // only — a criterion the rider skipped was skipped on purpose.
    fun moveOnFrom(criterion: Criterion) {
        val next = current.openAfter(criterion) ?: return
        val index = current.catalogue.criteria.indexOfFirst { it.id == next.id }
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
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
        val index = state.catalogue.criteria.indexOfFirst { it.id == expanded?.id }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    var explaining by remember { mutableStateOf(false) }
    if (explaining) BoundaryHelpDialog(onDismiss = { explaining = false })

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

    state.pendingEnd?.let {
        EndSegmentDialog(
            unapproved = state.carriedOver,
            selections = state.selections,
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
                    RecorderButton("Start", enabled, onMark = onStart)
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
                            onMark = { onEnd(it, SegmentAction.STOP, null) },
                            onMissedEnd = { onEnd(BoundaryKind.EARLIER, SegmentAction.STOP, it) },
                        )
                        RecorderButton(
                            label = "End, start next",
                            enabled = enabled,
                            onMark = { onEnd(it, SegmentAction.START_NEXT, null) },
                            onMissedEnd = { onEnd(BoundaryKind.EARLIER, SegmentAction.START_NEXT, it) },
                        )
                    }
                }
            }

            HoldHint(onExplain = { explaining = true })
        }
    }
}

/**
 * The one line that has to carry the hold gesture, since nothing on screen shows it any more, next to
 * the way out for a rider who wants the whole story.
 */
@Composable
private fun HoldHint(onExplain: () -> Unit) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        "Missed the moment? Hold the button, then slide.",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
    )
    OutlinedIconButton(onExplain, Modifier.size(44.dp)) {
        Text("?", style = MaterialTheme.typography.titleMedium)
    }
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

/** What the hold gesture is for, for a rider who has not met it before. */
@Composable
private fun BoundaryHelpDialog(onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Marking a boundary") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Tap a button and the boundary is where you are now — the start or end of the stretch " +
                    "you are describing.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Hold it instead and the boundary is marked as already passed. Use it when you notice a " +
                    "change in the path only after riding onto it: the segment is stored as having begun " +
                    "or ended before you pressed, and the exact spot is worked out later from your track.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Holding Start is all there is to it — the recording line then says \"started earlier\".",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Holding End raises two answers above your thumb, because how long ago it was decides " +
                    "whether the segment is worth keeping. Without letting go, slide up and to the left " +
                    "for under ${MissedEndGrace.inWholeSeconds} seconds — the end is stored that much " +
                    "earlier — or up and to the right for longer than that, which throws the segment " +
                    "away: a stretch that ended somewhere unknown is worse than none. Let go without " +
                    "sliding anywhere and nothing happens.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    },
    confirmButton = { Button(onDismiss) { Text("Got it") } },
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

/** How far the thumb has to travel off the button before it counts as having chosen something. */
private val SlideThreshold = 40.dp

private val PickerOptionWidth = 132.dp
private val PickerOptionHeight = 76.dp

/**
 * A boundary marker. A tap marks the boundary here and now; holding it marks one already passed — the
 * correction a rider reaches for after missing the moment, on the same button rather than beside it.
 *
 * On an end, holding does not answer on its own: how long ago the boundary was decides whether the
 * segment is worth keeping, so the hold raises the two answers above the thumb and the rider slides
 * onto one and lets go, in one movement, the way a phone's quick launch works. Lifting off without
 * having gone anywhere leaves the segment alone.
 *
 * Built out of a Surface because a Button has room for neither a long press nor what follows it.
 */
@Composable
private fun RowScope.RecorderButton(
    label: String,
    enabled: Boolean,
    onMark: (BoundaryKind) -> Unit,
    onMissedEnd: ((MissedEnd) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val threshold = with(LocalDensity.current) { SlideThreshold.toPx() }

    var picking by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<MissedEnd?>(null) }

    Surface(
        modifier = Modifier.weight(1f).heightIn(min = ActionButtonHeight).fillMaxHeight()
            .pointerInput(enabled, onMissedEnd) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()

                    // Released before the hold registered: an ordinary tap, on the boundary itself.
                    if (withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        } != null
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onMark(BoundaryKind.EXACT)
                        return@awaitEachGesture
                    }

                    // The heavier buzz, so the two boundaries feel apart without a look at the screen.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (onMissedEnd == null) {
                        onMark(BoundaryKind.EARLIER)
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    picking = true
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val slid = slideChoice(change.position - down.position, threshold)
                        if (slid != choice) {
                            choice = slid
                            // A tick as the thumb crosses onto an answer, since it covers the screen.
                            if (slid != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        change.consume()
                        if (!change.pressed) break
                    }

                    picking = false
                    choice?.let(onMissedEnd)
                    choice = null
                }
            },
        shape = ButtonDefaults.shape,
        color = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

            if (picking) {
                Popup(
                    alignment = Alignment.TopCenter,
                    offset = IntOffset(0, -with(LocalDensity.current) { (PickerOptionHeight + 16.dp).roundToPx() }),
                ) {
                    HowLatePicker(choice)
                }
            }
        }
    }
}

/**
 * Which answer the thumb is on. Direction rather than a target: up and to the left keeps the segment,
 * up and to the right throws it away, and anywhere near the button is still no answer at all — a
 * gesture that asks for no aim can be made without looking.
 */
private fun slideChoice(travelled: Offset, threshold: Float): MissedEnd? = when {
    travelled.y > -threshold -> null
    travelled.x < 0f -> MissedEnd.JUST_NOW
    else -> MissedEnd.LONGER
}

/** The two answers, raised above the thumb that is still holding the button down. */
@Composable
private fun HowLatePicker(choice: MissedEnd?) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    PickerOption(
        title = "↖ Under ${MissedEndGrace.inWholeSeconds} s",
        subtitle = "end goes back",
        selected = choice == MissedEnd.JUST_NOW,
        selectedColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    PickerOption(
        title = "Longer ↗",
        subtitle = "discard segment",
        selected = choice == MissedEnd.LONGER,
        selectedColor = MaterialTheme.colorScheme.error,
        selectedContentColor = MaterialTheme.colorScheme.onError,
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
    modifier = Modifier.size(PickerOptionWidth, PickerOptionHeight),
    shape = MaterialTheme.shapes.large,
    color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
    shadowElevation = if (selected) 8.dp else 2.dp,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}
