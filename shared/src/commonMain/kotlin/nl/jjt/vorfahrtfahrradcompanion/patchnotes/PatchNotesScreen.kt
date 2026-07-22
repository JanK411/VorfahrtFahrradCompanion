package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PatchNotesScreen(
    modifier: Modifier = Modifier,
    viewModel: PatchNotesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    PatchNotesContent(
        newNotes = state.newNotes,
        olderNotes = state.olderNotes,
        showOlder = state.showOlder,
        onToggleOlder = viewModel::toggleOlder,
        modifier = modifier,
    )
}

@Composable
private fun PatchNotesContent(
    newNotes: List<PatchNote>,
    olderNotes: List<PatchNote>,
    showOlder: Boolean,
    onToggleOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (newNotes.isEmpty()) {
            item {
                Text("You're up to date.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            items(newNotes) { PatchNoteItem(it) }
        }

        if (olderNotes.isNotEmpty()) {
            item {
                TextButton(onClick = onToggleOlder) {
                    Text(if (showOlder) "Hide older notes" else "Read older notes")
                }
            }
            if (showOlder) {
                items(olderNotes) { PatchNoteItem(it) }
            }
        }
    }
}

@Composable
private fun PatchNoteItem(note: PatchNote) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("v${note.version} · ${note.date}", style = MaterialTheme.typography.titleMedium)
        note.changes.forEach { change ->
            Text("• $change", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}
