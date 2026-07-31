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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
 * End — so the common case is one tap on the topmost exit.
 *
 * Every way out is a way out: taking them all or none ends the segment there and then, rather than
 * ticking boxes for a second press that a rider standing at a junction should not have to make.
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
                    "These still hold the last segment's answers. End keeping all of them, none of " +
                        "them, or tap the ones that do not describe this stretch and end with what is " +
                        "left.",
                    style = MaterialTheme.typography.bodyMedium,
                )

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
        // All four exits live in one column: the buttons row of a dialog puts them side by side, which
        // leaves four targets too narrow to hit without aiming.
        confirmButton = {
            Exits(
                kept = keep.size,
                total = ids.size,
                onKeepAll = { onConfirm(ids) },
                onAsSelected = { onConfirm(keep) },
                onDropAll = { onConfirm(emptySet()) },
                onBack = onDismiss,
            )
        },
    )
}

/** The four ways out, stacked full width so each one is a thumb-sized target. */
@Composable
private fun Exits(
    kept: Int,
    total: Int,
    onKeepAll: () -> Unit,
    onAsSelected: () -> Unit,
    onDropAll: () -> Unit,
    onBack: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Button(onKeepAll, ExitModifier) { ExitLabel("End, keeping all") }
    FilledTonalButton(onAsSelected, ExitModifier) { ExitLabel("End, keeping $kept of $total") }
    OutlinedButton(
        onDropAll,
        ExitModifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { ExitLabel("End, dropping all") }
    TextButton(onBack, ExitModifier) { ExitLabel("Back") }
}

private val ExitModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)

@Composable
private fun ExitLabel(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

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
