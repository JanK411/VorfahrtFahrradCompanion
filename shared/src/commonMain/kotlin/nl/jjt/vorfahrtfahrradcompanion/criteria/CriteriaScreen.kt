package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.ui.HoldMenuOption
import nl.jjt.vorfahrtfahrradcompanion.ui.KeepScreenAwake
import nl.jjt.vorfahrtfahrradcompanion.ui.Spotlight
import nl.jjt.vorfahrtfahrradcompanion.ui.WindowOrigin
import nl.jjt.vorfahrtfahrradcompanion.ui.holdAndSlide
import nl.jjt.vorfahrtfahrradcompanion.ui.secondsSince
import org.koin.compose.viewmodel.koinViewModel

/**
 * How long a pick-any criterion waits after a tap before moving on. Long enough to add a second
 * value, short enough that a rider who is done does not have to reach for the "Done" button.
 */
private const val MultiAdvanceMillis = 1500L

/**
 * Everything the criteria screen can ask of the ViewModel. Gathered into one object because the
 * screen needs most of them at once, and a dozen callback parameters is not a signature anyone reads.
 */
@Immutable
internal class CriteriaActions(
    val tap: (Criterion, String) -> Unit,
    val approve: (Criterion) -> Unit,
    val clearCarriedOver: () -> Unit,
    val approveAll: () -> Unit,
    val undo: () -> Unit,
    val copyPrevious: () -> Unit,
    val split: (Criterion, String) -> Unit,
    val start: (BoundaryKind) -> Unit,
    val end: (SegmentAction) -> Unit,
    val endAs: (SegmentAction, EndTiming) -> Unit,
    val answerTiming: (EndTiming) -> Unit,
    val cancelTiming: () -> Unit,
    val startRide: () -> Unit,
    val endRide: () -> Unit,
    val confirmEnd: (Set<String>) -> Unit,
    val cancelEnd: () -> Unit,
    val discardSegment: () -> Unit,
)

// TODO VF-116: guard an open segment with a LeaveGuard (see ServerConnectionScreen), so navigating away
//  mid-segment asks first instead of relying on the repository quietly holding on to it.
@Composable
fun CriteriaScreen(modifier: Modifier = Modifier) {
    val viewModel: CriteriaViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val endingRide by viewModel.endingRide.collectAsStateWithLifecycle()

    endingRide?.let {
        EndRideDialog(it, onSave = viewModel::saveRide, onDismiss = viewModel::cancelEndRide)
    }

    val actions = remember(viewModel) {
        CriteriaActions(
            tap = viewModel::onTap,
            approve = viewModel::onApprove,
            clearCarriedOver = viewModel::clearCarriedOver,
            approveAll = viewModel::approveAll,
            undo = viewModel::undo,
            copyPrevious = viewModel::copyPrevious,
            split = viewModel::splitSegment,
            start = viewModel::start,
            end = viewModel::end,
            endAs = viewModel::endAs,
            answerTiming = viewModel::answerTiming,
            cancelTiming = viewModel::cancelTiming,
            startRide = viewModel::startRide,
            endRide = viewModel::askToEndRide,
            confirmEnd = viewModel::confirmEnd,
            cancelEnd = viewModel::cancelEnd,
            discardSegment = viewModel::discardSegment,
        )
    }

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

        is CriteriaUiState.Ready ->
            Catalogue(s, viewModel.outcomes, viewModel.advances, actions, modifier)
    }
}

@Composable
private fun Catalogue(
    state: CriteriaUiState.Ready,
    outcomes: Flow<SegmentOutcome>,
    advances: Flow<Criterion>,
    actions: CriteriaActions,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // Three of the four outcomes mean nothing was stored, which the message has to carry at a glance
    // and not only in its words — amber, the same caution colour an unapproved value is written in.
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

    // The card the rider is working on. A tap pins it, so it cannot slide away under a thumb mid-answer,
    // and moving on unpins it again.
    var pinned by remember { mutableStateOf<String?>(null) }

    // How far down the list the rider has got. The flow leads from below it and only doubles back once
    // there is nothing left below to lead to.
    var settled by remember { mutableStateOf<Criterion?>(null) }

    // With nothing pinned the screen leads: it opens up the criterion to answer next — but only while
    // there is nothing left to review. A segment that inherited the last one's answers stays folded, so
    // the rider approves or changes them one line at a time instead of being dropped into the first one.
    val expanded = state.catalogue.criteria.firstOrNull { it.id == pinned }
        ?: state.leadingAfter(settled).takeIf { state.carriedOver.isEmpty() }

    // The card the rider's answer is wanted on, opened up or not: the only one lit in daylight.
    val attention = expanded ?: state.leadingAfter(settled)

    // A new segment starts at the top. The list keeps its scroll position across an end, and the first
    // open criterion is usually the same one as before, so nothing below would move the view back up.
    LaunchedEffect(state.segment) {
        pinned = null
        settled = null
        listState.scrollToItem(0)
    }

    // The collectors below outlive the composition they started in, so they read the state through this.
    val current by rememberUpdatedState(state)

    /**
     * Puts [criterion] second from the top rather than first, so that whatever was answered on the
     * way to it stays on screen above it. A value hit wrongly on a bumpy road is then put right
     * where the rider can still see it, instead of having to scroll back up for a card that left.
     */
    suspend fun bringUp(criterion: Criterion?) {
        val row = current.rowOf(criterion)
        if (row >= 0) listState.animateScrollToItem((row - 1).coerceAtLeast(0))
    }

    // Moving on: bring whatever comes after this one up the list, which is below it until the bottom
    // of the list is reached and whatever was skipped comes back round.
    fun moveOnFrom(criterion: Criterion) {
        scope.launch { bringUp(current.leadingAfter(criterion)) }
    }

    // Both ways of settling the whole carried-over list at once replace a screenful of answers on
    // one tap, so both say what they did and hold the door open on the way out.
    fun undoable(message: String) {
        discarded = false
        scope.launch {
            val undo = snackbarHostState.showSnackbar(message = message, actionLabel = "Undo")
            if (undo == SnackbarResult.ActionPerformed) actions.undo()
        }
    }

    LaunchedEffect(advances) {
        // collectLatest, so every further tap restarts the wait instead of stacking up advances.
        advances.collectLatest { criterion ->
            if (criterion.kind == CriterionKind.MULTI) delay(MultiAdvanceMillis)
            // Unless the rider has opened something else in the meantime, in which case they lead.
            if (pinned != criterion.id) return@collectLatest
            pinned = null
            settled = criterion

            // While anything else is still up for review nothing opens up, so the list has to be moved
            // on from here — the same step approving takes. Otherwise the criterion that opens up next
            // brings itself into view.
            if (current.carriedOver.any { it.id != criterion.id }) moveOnFrom(criterion)
        }
    }

    // Answer one and the next one comes to you — no scrolling while riding.
    LaunchedEffect(expanded?.id) { bringUp(expanded) }

    // A question in front of the rider is the thing wanting an answer, so it is lit like one.
    var discarding by remember { mutableStateOf(false) }
    if (discarding) {
        Spotlight(lit = true) {
            DiscardSegmentDialog(
                onConfirm = {
                    discarding = false
                    actions.discardSegment()
                },
                onDismiss = { discarding = false },
            )
        }
    }

    state.pendingTiming?.let {
        Spotlight(lit = true) {
            EndTimingDialog(
                action = it.action,
                onAnswer = actions.answerTiming,
                onDismiss = actions.cancelTiming,
            )
        }
    }

    state.pendingEnd?.let { pending ->
        Spotlight(lit = true) {
            EndSegmentDialog(
                // The list the question opened with: editing an answer in it approves that criterion,
                // which would take it out of a list read off the state mid-answer.
                carriedOver = pending.asked,
                selections = state.selections,
                approved = state.approved,
                onEdit = actions.tap,
                onConfirm = actions.confirmEnd,
                onDismiss = actions.cancelEnd,
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // The criteria describe the stretch being recorded, so they only make sense once one is open.
            if (state.segment is Segment.Open) Column(Modifier.fillMaxSize()) {
                // Above the list rather than in it: it is gone the moment anything is entered, so it
                // never has to be scrolled past, and it stays put until then.
                if (state.copyable != null) {
                    SegmentButton(
                        text = "Copy the previous segment",
                        icon = Icons.Filled.Refresh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        actions.copyPrevious()
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The very first row, so that answering anything below scrolls it out of the way for
                    // good — it is a decision about a fresh segment, not one to keep meeting.
                    if (state.carriedOver.isNotEmpty()) {
                        item(key = ClearCarriedOverKey) {
                            SegmentButton(
                                text = "Clear ${state.carriedOver.size} carried over",
                                icon = Icons.Filled.Close,
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                actions.clearCarriedOver()
                                undoable("Cleared — filling in from scratch")
                            }
                        }
                    }

                    items(state.catalogue.criteria, key = Criterion::id) { criterion ->
                        // Lit while it is the one being answered — or, where nothing is opened up
                        // because everything is still up for review, the one leading the list.
                        Spotlight(lit = criterion.id == attention?.id) {
                            CriterionCard(
                                criterion = criterion,
                                selected = state.selections[criterion],
                                state = state.stateOf(criterion),
                                expanded = criterion.id == expanded?.id,
                                onTapValue = { value ->
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    pinned = criterion.id
                                    actions.tap(criterion, value)
                                },
                                onOpen = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    pinned = criterion.id
                                },
                                onApprove = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    actions.approve(criterion)
                                    settled = criterion
                                    moveOnFrom(criterion)
                                },
                                onDone = {
                                    actions.approve(criterion)
                                    pinned = null
                                },
                                onSplit = { value -> actions.split(criterion, value) },
                            )
                        }
                    }

                    // The other way out of a carried-over list, at the far end of it: the rider who
                    // has read down the list and found nothing to change says so here, in one tap,
                    // rather than approving five cards they have just been through one by one.
                    if (state.carriedOver.isNotEmpty()) {
                        item(key = ApproveAllKey) {
                            SegmentButton(
                                text = "Approve all ${state.carriedOver.size}",
                                icon = Icons.Filled.Check,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                val approved = state.carriedOver.size
                                actions.approveAll()
                                undoable("Approved all $approved")
                            }
                        }
                    }
                }
            } else {
                Text(
                    if (state.ride is Ride.Open) "Start a segment to describe it." else "Start a ride to record segments.",
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

            // Segments are recorded into a ride, so there is nothing to start before one is running.
            if (state.ride !is Ride.Open) {
                Button(actions.startRide, Modifier.fillMaxWidth(), enabled) { Text("Start ride") }
            } else when (val segment = state.segment) {
                // Two buttons rather than one behind a hold: where a segment begins is a decision, not
                // a correction, and a rider who has already ridden onto the new stretch should not
                // have to hold anything down to say so.
                Segment.Idle -> {
                    ButtonRow {
                        RecorderButton(
                            label = "Start precise",
                            icon = Icons.Filled.Place,
                            enabled = enabled,
                            onTap = { actions.start(BoundaryKind.EXACT) },
                        )
                        RecorderButton(
                            label = "Start earlier",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            enabled = enabled,
                            onTap = { actions.start(BoundaryKind.EARLIER) },
                        )
                    }
                    // Only offered between segments: a ride ends where the last stretch of it did.
                    OutlinedButton(actions.endRide, Modifier.fillMaxWidth(), enabled) { Text("End ride") }
                }

                is Segment.Open -> {
                    Progress(
                        segment = segment,
                        describing = state.describing.size,
                        carried = state.carriedOver.size,
                        total = state.catalogue.criteria.size,
                        onDiscard = { discarding = true },
                    )
                    ButtonRow {
                        RecorderButton(
                            label = "End",
                            enabled = enabled,
                            onTap = { actions.end(SegmentAction.STOP) },
                            onPick = { actions.endAs(SegmentAction.STOP, it) },
                        )
                        RecorderButton(
                            label = "Start next",
                            enabled = enabled,
                            onTap = { actions.end(SegmentAction.START_NEXT) },
                            onPick = { actions.endAs(SegmentAction.START_NEXT, it) },
                        )
                    }
                }
            }
        }
    }
}

private const val ClearCarriedOverKey = "clear-carried-over"
private const val ApproveAllKey = "approve-all"

/** Where a criterion sits in the list, counting the clear button that leads it while there is one. */
private fun CriteriaUiState.Ready.rowOf(criterion: Criterion?): Int {
    val index = catalogue.criteria.indexOfFirst { it.id == criterion?.id }
    return if (index < 0) -1 else index + if (carriedOver.isEmpty()) 0 else 1
}

/** A decision about the segment as a whole rather than about one criterion. */
@Composable
private fun SegmentButton(
    text: String,
    icon: ImageVector,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = OutlinedButton(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/**
 * How long the open segment has been running, how much of the catalogue describes it, and — because
 * unapproved values are not stored — how much of it would be dropped right now.
 */
@Composable
private fun Progress(
    segment: Segment.Open,
    describing: Int,
    carried: Int,
    total: Int,
    onDiscard: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    val elapsed = secondsSince(segment.startedAt)
    val startedEarlier = if (segment.startKind == BoundaryKind.EARLIER) " · started earlier" else ""

    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Text(
            "● ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}$startedEarlier",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.Filled.Check,
            contentDescription = "Approved",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "$describing / $total",
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "$carried carried over, not approved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
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
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Discard")
        }
    },
    dismissButton = { TextButton(onDismiss) { Text("Keep recording") } },
)

