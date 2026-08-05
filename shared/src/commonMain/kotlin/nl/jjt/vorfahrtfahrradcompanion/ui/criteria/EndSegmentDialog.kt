package nl.jjt.vorfahrtfahrradcompanion.ui.criteria

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.*

/** The one precise target on a row; everything else about it opens the criterion up. */
private val APPROVE_BUTTON_SIZE = 56.dp

/**
 * What is asked of the rider on their way out of a segment that still carries the previous one's
 * answers: which of them describe this stretch too. Nothing starts approved — a tick the rider never
 * gave is not an answer — but nothing has to be settled one by one either: the exits take the lot
 * either way, so the common case is still one tap on the topmost one.
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
    carriedOver: List<Criterion>,
    catalogue: Catalogue,
    answers: Answers,
    approved: Set<Criterion>,
    onEdit: (Criterion, CriterionValue) -> Unit,
    onConfirm: (approve: Set<Criterion>) -> Unit,
    onDismiss: () -> Unit,
) {
    val asked = carriedOver.toSet()

    // What the rider has ticked. Nothing is in here to begin with: the exits below take the lot either
    // way, so a row nobody touched is a row nobody stood by.
    var keep by remember(asked) { mutableStateOf(emptySet<Criterion>()) }
    var editing by remember(asked) { mutableStateOf<Criterion?>(null) }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${carriedOver.size} carried over") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "These still describe the last stretch. Approve the ones that describe this one " +
                            "too — or tap an answer to change what it says.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    carriedOver.forEach { criterion ->
                        if (criterion == editing) {
                            EditingRow(
                                criterion = criterion,
                                values = catalogue[criterion],
                                selected = answers[criterion],
                                state = if (criterion in approved) CriterionState.APPROVED
                                else CriterionState.CARRIED_OVER,
                                onTapValue = { value ->
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onEdit(criterion, value)
                                    // Changing an answer is standing by it; nothing more is asked.
                                    keep = keep + criterion
                                    // A pick-one criterion is answered by that one tap.
                                    if (criterion.kind == CriterionKind.SINGLE) editing = null
                                },
                                onDone = { editing = null },
                            )
                        } else {
                            CarriedOverRow(
                                criterion = criterion,
                                selected = answers[criterion],
                                approve = criterion in keep,
                                onEdit = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    editing = criterion
                                },
                                onApprove = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    keep = if (criterion in keep) keep - criterion
                                    else keep + criterion
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
                approving = keep.size,
                total = asked.size,
                onApproveAll = { onConfirm(asked) },
                onApproveTicked = { onConfirm(keep) },
                onDropAll = { onConfirm(emptySet()) },
                onBack = onDismiss,
            )
        },
    )
}

/** The four ways out, stacked full width so each one is a thumb-sized target. */
@Composable
private fun Exits(
    approving: Int,
    total: Int,
    onApproveAll: () -> Unit,
    onApproveTicked: () -> Unit,
    onDropAll: () -> Unit,
    onBack: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Button(onApproveAll, ExitModifier) { ExitLabel(Icons.Filled.Check, "End · approve all") }
    FilledTonalButton(onApproveTicked, ExitModifier) {
        ExitLabel(Icons.Filled.Check, "End · approve $approving of $total")
    }
    OutlinedButton(
        onDropAll,
        ExitModifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) { ExitLabel(Icons.Filled.Close, "End · drop all") }
    TextButton(onBack, ExitModifier) { ExitLabel(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}

private val ExitModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)

@Composable
private fun ExitLabel(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/**
 * One criterion up for a decision: the tick stands by it, and the values themselves open it up to be
 * changed. The pencil marks that they do — the same hint a carried-over card carries on the list, so the
 * gesture is the one the rider already knows.
 *
 * There is no cross beside the tick: leaving a row alone already drops it, and a tick tapped by mistake
 * comes off again on the next tap.
 */
@Composable
private fun CarriedOverRow(
    criterion: Criterion,
    selected: Set<CriterionValue>,
    approve: Boolean,
    onEdit: () -> Unit,
    onApprove: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(
            if (approve) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        )
        .heightIn(min = CRITERION_ROW_HEIGHT),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier = Modifier.weight(1f).clickable(onClick = onEdit)
            .heightIn(min = CRITERION_ROW_HEIGHT)
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
                selected.joinToString(" · ") { it.id },
                style = MaterialTheme.typography.titleLarge,
                color = if (approve) MaterialTheme.colorScheme.onSecondaryContainer
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

    // Filled once the row is approved, flat while it is still nobody's answer.
    FilledIconButton(
        onClick = onApprove,
        modifier = Modifier.padding(end = 8.dp).size(APPROVE_BUTTON_SIZE),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (approve) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (approve) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "Approve ${criterion.label()}",
            modifier = Modifier.size(28.dp),
        )
    }
}

/** A row opened up to be changed, on the same buttons the criterion is answered with on the list. */
@Composable
private fun EditingRow(
    criterion: Criterion,
    values: List<CriterionValue>,
    selected: Set<CriterionValue>,
    state: CriterionState,
    onTapValue: (CriterionValue) -> Unit,
    onDone: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant),
) {
    ExpandedCriterion(
        criterion = criterion,
        values = values,
        selected = selected,
        state = state,
        onTapValue = onTapValue,
        onDone = onDone,
    )
}
