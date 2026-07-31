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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private val RowHeight = 64.dp

/** The one precise target on a row; everything else about it opens the criterion up. */
private val KeepButtonSize = 56.dp

/**
 * What is asked of the rider on their way out of a segment that still carries the previous one's
 * answers: which of them describe this stretch too. Nothing starts decided — a tick the rider never gave
 * is not an answer — but nothing has to be decided one by one either: the exits take the lot either way,
 * so the common case is still one tap on the topmost one.
 *
 * A row that is nearly right needs no tick at all: tapping it opens the criterion up, and picking a value
 * both changes it and stands by it, so a stretch that differs in one answer can be corrected on the way
 * out instead of costing the whole criterion.
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

    // What the rider has ticked. Nothing is in here to begin with: the exits below take the lot either
    // way, so a row nobody touched is a row nobody stood by.
    var keep by remember(ids) { mutableStateOf(emptySet<String>()) }
    var editing by remember(ids) { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${unapproved.size} still unapproved") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "These still hold the last segment's answers. End keeping all of them, none of " +
                        "them, or tick the ones that describe this stretch too — tap an answer itself " +
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
                                onKeep = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    keep = if (criterion.id in keep) keep - criterion.id
                                    else keep + criterion.id
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
 * One criterion up for a decision: the tick stands by it, and the values themselves open it up to be
 * changed. The pencil marks that they do — the same hint a carried-over card carries on the list, so the
 * gesture is the one the rider already knows.
 *
 * There is no cross beside the tick: leaving a row alone already drops it, and a tick tapped by mistake
 * comes off again on the next tap.
 */
@Composable
private fun UnapprovedRow(
    criterion: Criterion,
    values: Set<String>,
    keep: Boolean,
    onEdit: () -> Unit,
    onKeep: () -> Unit,
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
            )
        }
        Icon(
            Icons.Filled.Edit,
            contentDescription = "Change",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Filled once the row is kept, flat while it is still nobody's answer.
    FilledIconButton(
        onClick = onKeep,
        modifier = Modifier.padding(end = 8.dp).size(KeepButtonSize),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (keep) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (keep) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "Keep ${criterion.label()}",
            modifier = Modifier.size(28.dp),
        )
    }
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
