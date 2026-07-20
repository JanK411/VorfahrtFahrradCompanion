package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

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

        is CriteriaUiState.Ready -> Catalogue(s, viewModel::onSelect, viewModel::submit, modifier)
    }
}

@Composable
private fun Catalogue(
    state: CriteriaUiState.Ready,
    onSelect: (Criterion, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // The ViewModel returns to Idle only after a successful POST, so InFlight → Idle is the success edge.
    var wasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(state.submitState) {
        if (wasInFlight && state.submitState is SubmitState.Idle) {
            snackbarHostState.showSnackbar("Observation submitted")
        }
        wasInFlight = state.submitState is SubmitState.InFlight
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.catalogue.criteria, key = Criterion::id) { criterion ->
                    CriterionSection(criterion, state.selections[criterion.id].orEmpty(), onSelect)
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (state.submitState as? SubmitState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSubmit,
                enabled = state.submitState !is SubmitState.InFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitState is SubmitState.InFlight) "Submitting…" else "Submit")
            }
        }
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
        Text(criterion.id, style = MaterialTheme.typography.titleSmall)
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
