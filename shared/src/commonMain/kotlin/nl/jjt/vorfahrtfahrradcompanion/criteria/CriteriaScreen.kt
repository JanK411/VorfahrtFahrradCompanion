package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
            onKeepAll = viewModel::onKeepAll,
            onDiscardUnapproved = viewModel::onDiscardUnapproved,
            onUndoClear = viewModel::onUndoClear,
            onStart = viewModel::start,
            onEnd = viewModel::end,
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
    onKeepAll: () -> Unit,
    onDiscardUnapproved: () -> Unit,
    onUndoClear: () -> Unit,
    onStart: (BoundaryKind) -> Unit,
    onEnd: (BoundaryKind, SegmentAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var discarded by remember { mutableStateOf(false) }
    LaunchedEffect(outcomes) {
        outcomes.collect { outcome ->
            discarded = outcome == SegmentOutcome.DISCARDED
            snackbarHostState.showSnackbar(
                when (outcome) {
                    SegmentOutcome.SAVED -> "Segment saved"
                    SegmentOutcome.DISCARDED -> "Nothing confirmed — segment discarded"
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
    //
    // With nothing pinned the screen leads: it expands the criterion to answer next — but only while
    // there is nothing left to review. A segment that inherited the last one's answers stays folded, so
    // the rider approves or changes them one line at a time instead of being dropped into the first one.
    var pinned by remember { mutableStateOf<String?>(null) }
    val expanded = state.catalogue.criteria.firstOrNull { it.id == pinned }
        ?: state.nextOpen.takeIf { state.carriedOver.isEmpty() }

    // A new segment starts at the top. The list keeps its scroll position across an end, and the first
    // open criterion is usually the same one as before, so nothing below would move the view back up.
    LaunchedEffect(state.segment) {
        pinned = null
        listState.scrollToItem(0)
    }

    LaunchedEffect(advances) {
        // collectLatest, so every further tap restarts the wait instead of stacking up advances.
        advances.collectLatest { criterion ->
            if (criterion.kind == CriterionKind.MULTI) delay(MultiAdvanceMillis)
            // Unless the rider has opened something else in the meantime, in which case they lead.
            if (pinned == criterion.id) pinned = null
        }
    }

    // Answer one at the top and the next one comes to you — no scrolling while riding.
    LaunchedEffect(expanded?.id) {
        val index = state.catalogue.criteria.indexOfFirst { it.id == expanded?.id }
        if (index >= 0) listState.animateScrollToItem(index)
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
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinned = criterion.id
                                onTap(criterion, value)
                            },
                            onOpen = { pinned = criterion.id },
                            onApprove = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm(criterion)
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
                    RecorderButton("Start now", enabled, BoundaryKind.EXACT, onStart)
                    RecorderButton("Started earlier", enabled, BoundaryKind.EARLIER, onStart)
                }

                is Segment.Open -> {
                    Progress(segment, state.confirmed.size, state.carriedOver.size, state.catalogue.criteria.size)

                    // The wholesale answers to a segment full of carried-over values: all of it still
                    // holds, or none of it does.
                    val carried = state.carriedOver.size
                    if (carried > 0) {
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onKeepAll()
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) {
                                Text(
                                    "Approve all $carried",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDiscardUnapproved()
                                    discarded = false
                                    scope.launch {
                                        val undo = snackbarHostState.showSnackbar(
                                            message = "Discarded $carried",
                                            actionLabel = "Undo",
                                        )
                                        if (undo == SnackbarResult.ActionPerformed) onUndoClear()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) {
                                Text(
                                    "Discard $carried",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    ButtonRow {
                        RecorderButton("End now", enabled, BoundaryKind.EXACT) { onEnd(it, SegmentAction.STOP) }
                        RecorderButton("End now, start next", enabled, BoundaryKind.EXACT) {
                            onEnd(it, SegmentAction.START_NEXT)
                        }
                    }

                    // The late-boundary corrections are the exception, so they stay out of the way until
                    // the rider actually missed the moment.
                    var showEarlier by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showEarlier = !showEarlier },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Text(if (showEarlier) "Hide" else "Missed the moment?")
                    }
                    AnimatedVisibility(showEarlier) {
                        ButtonRow {
                            RecorderButton("Ended earlier", enabled, BoundaryKind.EARLIER) {
                                onEnd(it, SegmentAction.STOP)
                            }
                            RecorderButton("Ended earlier, start next", enabled, BoundaryKind.EARLIER) {
                                onEnd(it, SegmentAction.START_NEXT)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * How long the open segment has been running, how much of the catalogue it carries, and — because
 * unconfirmed values are not stored — how much of it would be dropped right now.
 */
@Composable
private fun Progress(segment: Segment.Open, confirmed: Int, carried: Int, total: Int) = Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    val elapsed = secondsSince(segment.startedAt)
    val startedEarlier = if (segment.startKind == BoundaryKind.EARLIER) " · started earlier" else ""

    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(
            "● Recording ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}$startedEarlier",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$confirmed / $total",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (carried > 0) {
        Text(
            "$carried carried over, won't be saved unless you keep them",
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

/**
 * One boundary marker, styled after the [kind] it records: filled for "I pressed at the boundary", amber
 * outline for the "it already happened earlier" correction a rider reaches for after missing the moment.
 */
@Composable
private fun RowScope.RecorderButton(
    label: String,
    enabled: Boolean,
    kind: BoundaryKind,
    onClick: (BoundaryKind) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val click = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick(kind)
    }
    val modifier = Modifier.weight(1f).heightIn(min = ActionButtonHeight).fillMaxHeight()
    val text = @Composable {
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }

    when (kind) {
        BoundaryKind.EXACT -> Button(click, modifier, enabled) { text() }
        BoundaryKind.EARLIER -> OutlinedButton(
            click,
            modifier,
            enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
        ) { text() }
    }
}
