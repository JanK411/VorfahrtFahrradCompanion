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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val RowHeight = 64.dp

/** Small enough for two of them to sit beside the values, big enough to hit without aiming. */
private val DecisionButtonSize = 52.dp

/**
 * What is asked of the rider on their way out of a segment that still carries the previous one's
 * answers: which of them describe this stretch too. Everything starts kept — the values are here because
 * the last stretch had them, and a rider who wanted none of them would have said so before reaching for
 * End — so the common case is one tap on the topmost exit.
 *
 * A row that is nearly right needs neither: tapping it opens the criterion up, and picking a value both
 * changes it and stands by it, so a stretch that differs in one answer can be corrected on the way out
 * instead of costing the whole criterion.
 *
 * Every way out is a way out: taking them all or none ends the segment there and then, rather than
 * ticking boxes for a second press that a rider standing at a junction should not have to make.
 */
@Composable
internal fun EndSegmentDialog(
    unapproved: List<Criterion>,
    selections: Selections,
    reviewed: Set<String>,
    onEdit: (Criterion, String) -> Unit,
    onConfirm: (approve: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ids = unapproved.map(Criterion::id).toSet()
    var keep by remember(ids) { mutableStateOf(ids) }
    var editing by remember(ids) { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${unapproved.size} still unapproved") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "These still hold the last segment's answers. End keeping all of them, none of " +
                        "them, or say per answer what to do with it — tick it, cross it out, or tap it " +
                        "to change what it says.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    unapproved.forEach { criterion ->
                        if (criterion.id == editing) {
                            EditingRow(
                                criterion = criterion,
                                selected = selections[criterion],
                                review = if (criterion.id in reviewed) Review.CONFIRMED else Review.CARRIED,
                                onTapValue = { value ->
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onEdit(criterion, value)
                                    // Changing an answer is standing by it; nothing more is asked.
                                    keep = keep + criterion.id
                                    // A single-choice criterion is answered by that one tap.
                                    if (criterion.kind == CriterionKind.SINGLE) editing = null
                                },
                                onDone = { editing = null },
                            )
                        } else {
                            UnapprovedRow(
                                criterion = criterion,
                                values = selections[criterion],
                                keep = criterion.id in keep,
                                onEdit = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    editing = criterion.id
                                },
                                onDecide = { approve ->
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    keep = if (approve) keep + criterion.id else keep - criterion.id
                                },
                            )
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

/**
 * One criterion up for a decision: the tick stands by it, the cross drops it, and the values themselves
 * open it up to be changed. The pencil marks that they do — the same hint a carried-over card carries on
 * the list, so the gesture is the one the rider already knows.
 */
@Composable
private fun UnapprovedRow(
    criterion: Criterion,
    values: Set<String>,
    keep: Boolean,
    onEdit: () -> Unit,
    onDecide: (approve: Boolean) -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(
            if (keep) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        )
        .heightIn(min = RowHeight),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier = Modifier.weight(1f).clickable(onClick = onEdit)
            .heightIn(min = RowHeight)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Icon(
            Icons.Filled.Edit,
            contentDescription = "Change",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    DecisionButton(
        icon = Icons.Filled.Close,
        description = "Drop ${criterion.label()}",
        active = !keep,
        activeColor = MaterialTheme.colorScheme.error,
        activeContentColor = MaterialTheme.colorScheme.onError,
        onClick = { onDecide(false) },
    )
    DecisionButton(
        icon = Icons.Filled.Check,
        description = "Keep ${criterion.label()}",
        active = keep,
        activeColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        onClick = { onDecide(true) },
        modifier = Modifier.padding(end = 8.dp),
    )
}

/**
 * One of the two answers to a row. Both stay on screen whichever way the row currently leans — the filled
 * one is what happens on End, and the flat one is the way back — so neither decision hides behind the
 * other.
 */
@Composable
private fun DecisionButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    activeColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = FilledIconButton(
    onClick = onClick,
    modifier = modifier.size(DecisionButtonSize),
    colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = if (active) activeColor else Color.Transparent,
        contentColor = if (active) activeContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
    ),
) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(28.dp))
}

/** A row opened up to be changed, on the same buttons the criterion is answered with on the list. */
@Composable
private fun EditingRow(
    criterion: Criterion,
    selected: Set<String>,
    review: Review,
    onTapValue: (String) -> Unit,
    onDone: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant),
) {
    ExpandedCriterion(
        criterion = criterion,
        selected = selected,
        review = review,
        onTapValue = onTapValue,
        onNext = onDone,
    )
}
