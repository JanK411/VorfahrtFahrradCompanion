package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jjt.vorfahrtfahrradcompanion.ui.secondsSince
import org.koin.compose.viewmodel.koinViewModel

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
            onSelect = viewModel::onSelect,
            onStart = viewModel::start,
            onEnd = viewModel::end,
            onStartRide = viewModel::startRide,
            onEndRide = viewModel::askToEndRide,
            modifier = modifier,
        )
    }
}

@Composable
private fun Catalogue(
    state: CriteriaUiState.Ready,
    onSelect: (Criterion, String) -> Unit,
    onStart: (BoundaryKind) -> Unit,
    onEnd: (BoundaryKind, SegmentAction) -> Unit,
    onStartRide: () -> Unit,
    onEndRide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // The ViewModel returns to Idle only after a successful write, so InFlight → Idle is the success edge.
    var wasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(state.saveState) {
        if (wasInFlight && state.saveState is SaveState.Idle) {
            snackbarHostState.showSnackbar("Segment saved")
        }
        wasInFlight = state.saveState is SaveState.InFlight
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // The criteria describe the stretch being recorded, so they only make sense once one is open.
            if (state.segment is Segment.Open) {
                LazyColumn(
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.catalogue.criteria, key = Criterion::id) { criterion ->
                        CriterionSection(criterion, state.selections[criterion], onSelect)
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
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (state.saveState as? SaveState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            val enabled = state.saveState !is SaveState.InFlight

            // Segments are recorded into a ride, so there is nothing to start before one is running.
            if (state.ride !is Ride.Open) {
                Button(onStartRide, Modifier.fillMaxWidth(), enabled) { Text("Start ride") }
            } else when (val segment = state.segment) {
                Segment.Idle -> {
                    ButtonRow {
                        RecorderButton("Start now", enabled, BoundaryKind.EXACT, onStart)
                        RecorderButton("Started earlier", enabled, BoundaryKind.EARLIER, onStart)
                    }
                    // Only offered between segments: a ride ends where the last stretch of it did.
                    OutlinedButton(onEndRide, Modifier.fillMaxWidth(), enabled) { Text("End ride") }
                }

                is Segment.Open -> {
                    RecordingStatus(segment)
                    ButtonRow {
                        RecorderButton("End now", enabled, BoundaryKind.EXACT) { onEnd(it, SegmentAction.STOP) }
                        RecorderButton("End now, start next", enabled, BoundaryKind.EXACT) {
                            onEnd(it, SegmentAction.START_NEXT)
                        }
                    }
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

/** How long the open segment has been running, and whether its start was already a late one. */
@Composable
private fun RecordingStatus(segment: Segment.Open) {
    val elapsed = secondsSince(segment.startedAt)
    val startedEarlier = if (segment.startKind == BoundaryKind.EARLIER) " · started earlier" else ""
    Text(
        "● Recording ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}$startedEarlier",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
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
    val modifier = Modifier.weight(1f).fillMaxHeight()
    val text = @Composable { Text(label, textAlign = TextAlign.Center) }

    when (kind) {
        BoundaryKind.EXACT -> Button({ onClick(kind) }, modifier, enabled) { text() }
        BoundaryKind.EARLIER -> OutlinedButton(
            { onClick(kind) },
            modifier,
            enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
        ) { text() }
    }
}

/**
 * Renders any criterion. [CriterionKind] only reaches the click reducer, never the layout — that is
 * what lets this screen render a catalogue it has never seen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CriterionSection(
    criterion: Criterion,
    selected: Set<String>,
    onSelect: (Criterion, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            criterion.id,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            criterion.values.forEach { value ->
                FilterChip(
                    selected = value in selected,
                    onClick = { onSelect(criterion, value) },
                    label = { Text(value) },
                )
            }
        }
    }
}
