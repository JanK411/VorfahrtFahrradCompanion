package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** How far along a criterion is in the current segment. This drives the whole look of its card. */
internal enum class CriterionState {
    /** Nothing chosen — the rider has this one still to answer. */
    OPEN,

    /** Holding the previous segment's answer, which is a suggestion until it is approved. */
    CARRIED_OVER,

    /** Stood by for this segment, so it is what the segment would be stored with. */
    APPROVED,
}

internal fun CriteriaUiState.Ready.stateOf(criterion: Criterion): CriterionState = when {
    selections[criterion].isEmpty() -> CriterionState.OPEN
    criterion.id in approved -> CriterionState.APPROVED
    else -> CriterionState.CARRIED_OVER
}

/** Big enough to hit on a bumpy road without looking for long. */
private val ValueButtonHeight = 72.dp
internal val CriterionRowHeight = 64.dp

/** The one precise target on a folded card; everything around it opens the criterion instead. */
private val ApproveButtonSize = 64.dp

/**
 * One criterion. Folded it is a single line — label, values, and whether they still need approving —
 * so a filled-in catalogue stays scannable; expanded it is a column of buttons sized for a mounted phone.
 *
 * Renders any criterion: [CriterionKind] only reaches the tap reducer and the "Done" button, never the
 * layout, which is what lets this screen render a catalogue it has never seen.
 */
@Composable
internal fun CriterionCard(
    criterion: Criterion,
    selected: Set<String>,
    state: CriterionState,
    expanded: Boolean,
    onTapValue: (String) -> Unit,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                expanded -> scheme.surface
                state == CriterionState.APPROVED -> scheme.secondaryContainer
                else -> scheme.surfaceVariant
            },
        ),
        // The amber outline is this app's "needs your attention" mark.
        border = when {
            expanded -> BorderStroke(2.dp, scheme.primary)
            state == CriterionState.CARRIED_OVER -> BorderStroke(1.dp, scheme.tertiary)
            else -> null
        },
    ) {
        when {
            expanded -> ExpandedCriterion(criterion, selected, state, onTapValue, onDone)
            state == CriterionState.CARRIED_OVER ->
                CarriedOverCriterion(criterion, selected, onOpen, onApprove)

            else -> FoldedCriterion(criterion, selected, state, onOpen)
        }
    }
}

/**
 * A criterion still holding the previous segment's answer. It stays folded — the rider is reviewing what
 * is already there, not filling anything in — and splits into the only two things worth doing to it: the
 * approve button, and everything else, which opens it up to pick something else.
 *
 * The pencil is a hint rather than a button: it sits in the card's own tap area, so hitting it works
 * regardless, and it costs a rider nothing to miss.
 */
@Composable
private fun CarriedOverCriterion(
    criterion: Criterion,
    selected: Set<String>,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = CriterionRowHeight)
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CriterionSummary(criterion, selected, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))

    Icon(
        Icons.Filled.Edit,
        contentDescription = "Change",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledIconButton(onApprove, Modifier.size(ApproveButtonSize)) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "Approve ${criterion.label()}",
            modifier = Modifier.size(32.dp),
        )
    }
}

/** A criterion the rider is done with, or has not started: one line, tapped to open it up again. */
@Composable
private fun FoldedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    state: CriterionState,
    onOpen: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = CriterionRowHeight)
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val approved = state == CriterionState.APPROVED

    CriterionSummary(
        criterion,
        selected,
        valueColor = if (approved) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )

    if (approved) {
        Icon(Icons.Filled.Check, contentDescription = "Approved", tint = MaterialTheme.colorScheme.primary)
    }
}

/** The label and the values, the two lines every folded card leads with. */
@Composable
private fun CriterionSummary(
    criterion: Criterion,
    selected: Set<String>,
    valueColor: Color,
    modifier: Modifier = Modifier,
) = Column(modifier, Arrangement.spacedBy(2.dp)) {
    Text(
        criterion.label(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        selected.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "—",
        style = MaterialTheme.typography.titleLarge,
        color = valueColor,
    )
}

/**
 * A criterion opened up to be answered: every value as a full-width button, one under the other, sized
 * for a mounted phone. One column rather than two, so a value is read and hit without aiming sideways,
 * and so a long label has the whole width to itself instead of wrapping in half of it.
 */
@Composable
internal fun ExpandedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    state: CriterionState,
    onTapValue: (String) -> Unit,
    onDone: () -> Unit,
) = Column(
    modifier = Modifier.padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(
            criterion.label(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (criterion.kind == CriterionKind.SINGLE) "pick one" else "pick any",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        criterion.values.forEach { value ->
            ValueButton(
                label = value,
                state = when {
                    value !in selected -> CriterionState.OPEN
                    state == CriterionState.CARRIED_OVER -> CriterionState.CARRIED_OVER
                    else -> CriterionState.APPROVED
                },
                onClick = { onTapValue(value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // A pick-any criterion has no natural last tap, so it also gets a way to say "done here". Nothing
    // about it may read as one more value: a rule cuts it off from the values, and where a value is a
    // squared-off outlined button, this is a filled pill carrying an arrow.
    if (criterion.kind == CriterionKind.MULTI) {
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = CriterionRowHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Text("Done — next", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * One value. Filled means approved for this segment, greyed-out means carried over and still waiting for
 * a nod, outlined means not chosen.
 */
@Composable
private fun ValueButton(
    label: String,
    state: CriterionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chosen values carry a check as well as a fill, so the state survives sunlight and a glance.
    val content = @Composable {
        if (state != CriterionState.OPEN) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
    val sized = modifier.heightIn(min = ValueButtonHeight)
    val padding = ButtonDefaults.ContentPadding

    when (state) {
        CriterionState.APPROVED -> Button(onClick, sized, contentPadding = padding) { content() }

        CriterionState.CARRIED_OVER -> FilledTonalButton(
            onClick,
            sized,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) { content() }

        // A two-pixel edge rather than the default hairline: in sunlight a thin outline is the first
        // thing to disappear, and an unchosen value has nothing else to show it is a button at all.
        CriterionState.OPEN -> OutlinedButton(
            onClick,
            sized,
            contentPadding = padding,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) { content() }
    }
}
