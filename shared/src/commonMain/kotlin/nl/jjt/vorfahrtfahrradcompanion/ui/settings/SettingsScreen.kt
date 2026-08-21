package nl.jjt.vorfahrtfahrradcompanion.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.CategorizationVariant
import org.koin.compose.viewmodel.koinViewModel

/** Settings landing page: a menu of sub-pages. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    onOpenServerConnection: () -> Unit = {},
    onOpenLocation: () -> Unit = {},
    onOpenPatchNotes: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
        SettingsRow("Server connection", onOpenServerConnection)
        SettingsRow("Location", onOpenLocation)
        SettingsRow("What's New", onOpenPatchNotes)
        CategorizationRow(state.categorization, viewModel::choose)
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Which of the two criteria designs to ride with. Not a sub-page: it is meant to be flipped between
 * segments rather than filled in, and it goes altogether once one of the designs has won.
 */
@Composable
private fun CategorizationRow(
    selected: CategorizationVariant?,
    onPick: (CategorizationVariant) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Categorising", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategorizationVariant.entries.forEach { variant ->
                FilterChip(
                    selected = variant == selected,
                    onClick = { onPick(variant) },
                    label = { Text(variant.label) },
                )
            }
        }
    }
}
