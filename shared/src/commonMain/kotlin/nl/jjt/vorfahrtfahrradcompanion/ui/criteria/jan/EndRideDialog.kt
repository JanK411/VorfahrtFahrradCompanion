package nl.jjt.vorfahrtfahrradcompanion.ui.criteria.jan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideSummary
import nl.jjt.vorfahrtfahrradcompanion.ui.common.timeOfDay

/**
 * What the rider signs a ride off with: what it came to, and a name for it if they want one. The ride is
 * already over by the time this is up — dismissing takes it back and lets the ride run on.
 */
@Composable
internal fun EndRideDialog(
    summary: RideSummary,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End ride") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${summary.startedAt.timeOfDay()} – ${summary.endedAt.timeOfDay()}")
                Text(
                    when (summary.segments) {
                        0 -> "No segments recorded — this ride will be discarded."
                        1 -> "1 segment recorded."
                        else -> "${summary.segments} segments recorded."
                    },
                    color = if (summary.segments == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (summary.segments > 0) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(name) }) {
                Text(if (summary.segments == 0) "Discard ride" else "Save ride")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Keep riding") } },
    )
}
