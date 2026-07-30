package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val RowHeight = 64.dp

/**
 * What is asked of the rider on their way out of a segment that still carries the previous one's
 * answers: which of them describe this stretch too. Everything starts kept — the values are here because
 * the last stretch had them, and a rider who wanted none of them would have said so before reaching for
 * End — so the common case is one tap on the confirm button.
 */
@Composable
internal fun EndSegmentDialog(
    unapproved: List<Criterion>,
    selections: Selections,
    onConfirm: (approve: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ids = unapproved.map(Criterion::id).toSet()
    var keep by remember(ids) { mutableStateOf(ids) }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${unapproved.size} still unapproved") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "These still hold the last segment's answers. Tap any that do not describe this " +
                        "stretch — those are left out of it.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ keep = ids }) { Text("Keep all") }
                    TextButton({ keep = emptySet() }) { Text("Drop all") }
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    unapproved.forEach { criterion ->
                        UnapprovedRow(criterion, selections[criterion], criterion.id in keep) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            keep = if (criterion.id in keep) keep - criterion.id else keep + criterion.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button({ onConfirm(keep) }, Modifier.heightIn(min = 56.dp)) {
                Text("End segment", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onDismiss, Modifier.heightIn(min = 56.dp)) {
                Text("Back", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

/** One criterion up for a decision. The whole row toggles it, so it takes no aim to answer. */
@Composable
private fun UnapprovedRow(
    criterion: Criterion,
    values: Set<String>,
    keep: Boolean,
    onToggle: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(
            if (keep) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        )
        .clickable(onClick = onToggle)
        .heightIn(min = RowHeight)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        if (keep) Icons.Filled.Check else Icons.Filled.Close,
        contentDescription = if (keep) "Kept" else "Dropped",
        modifier = Modifier.size(28.dp),
        tint = if (keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
        Text(
            criterion.label(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            values.joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            color = if (keep) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (keep) null else TextDecoration.LineThrough,
        )
    }
}
