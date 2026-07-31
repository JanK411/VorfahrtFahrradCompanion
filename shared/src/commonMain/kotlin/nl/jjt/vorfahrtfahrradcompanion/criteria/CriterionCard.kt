package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
internal enum class Review { CONFIRMED, CARRIED, OPEN }

internal fun CriteriaUiState.Ready.reviewOf(criterion: Criterion): Review = when {
    selections[criterion].isEmpty() -> Review.OPEN
    criterion.id in reviewed -> Review.CONFIRMED
    else -> Review.CARRIED
}

/** Big enough to hit on a bumpy road without looking for long. */
private val ValueButtonHeight = 72.dp
private val RowHeight = 64.dp

/** The one precise target on a folded card; everything around it opens the criterion instead. */
private val ApproveButtonSize = 64.dp

/**
 * One criterion. Collapsed it is a single line — label, values, and whether they still need approving —
 * so a filled-in catalogue stays scannable; expanded it is a grid of buttons sized for a mounted phone.
 *
 * Renders any criterion: [CriterionKind] only reaches the tap reducer and the "Next" button, never the
 * layout, which is what lets this screen render a catalogue it has never seen.
 */
@Composable
internal fun CriterionCard(
    criterion: Criterion,
    selected: Set<String>,
    review: Review,
    expanded: Boolean,
    onTapValue: (String) -> Unit,
    onOpen: () -> Unit,
    onApprove: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        // Any folded card opens on a tap anywhere — the whole card is the target, so a knock in the road
        // costs nothing. Only the approve button carves a piece out of it.
        modifier = Modifier.fillMaxWidth().clickable(enabled = !expanded, onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = when {
                expanded -> scheme.surface
                review == Review.CONFIRMED -> scheme.secondaryContainer
                else -> scheme.surfaceVariant
            },
        ),
        // The amber outline is this app's "needs your attention" mark, as on the "earlier" buttons.
        border = when {
            expanded -> BorderStroke(2.dp, scheme.primary)
            review == Review.CARRIED -> BorderStroke(1.dp, scheme.tertiary)
            else -> null
        },
    ) {
        when {
            expanded -> ExpandedCriterion(criterion, selected, review, onTapValue, onNext)
            review == Review.CARRIED -> CarriedCriterion(criterion, selected, onApprove)
            else -> CollapsedCriterion(criterion, selected, confirmed = review == Review.CONFIRMED)
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
private fun CarriedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    onApprove: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 8.dp),
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
        Icon(Icons.Filled.Check, contentDescription = "Approve", modifier = Modifier.size(32.dp))
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
 * A criterion opened up to be answered: every value as a button, sized for a mounted phone. Shared with
 * the end-of-segment question, so a value changed on the way out is picked the same way as one on the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpandedCriterion(
    criterion: Criterion,
    selected: Set<String>,
    review: Review,
    onTapValue: (String) -> Unit,
    onNext: () -> Unit,
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        criterion.values.forEach { value ->
            ValueButton(
                label = value,
                state = when {
                    value !in selected -> Review.OPEN
                    review == Review.CARRIED -> Review.CARRIED
                    else -> Review.CONFIRMED
                },
                onClick = { onTapValue(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // A multi-choice criterion has no natural last tap, so it also gets a way to say "done here".
    // Nothing about it may read as one more value: a rule cuts it off from the grid, and where a value
    // is a squared-off half-width button, this is a full-width filled pill carrying an arrow.
    if (criterion.kind == CriterionKind.MULTI) {
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight),
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

@Composable
private fun CollapsedCriterion(criterion: Criterion, selected: Set<String>, confirmed: Boolean) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CriterionSummary(
        criterion,
        selected,
        valueColor = if (confirmed) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )

    if (confirmed) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "approved",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * One value. Filled means confirmed for this segment, greyed-out means carried over and still waiting for
 * a nod, outlined means not chosen.
 */
@Composable
private fun ValueButton(
    label: String,
    state: Review,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chosen values carry a check as well as a fill, so the state survives sunlight and a glance.
    val content = @Composable {
        if (state != Review.OPEN) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
    val sized = modifier.heightIn(min = ValueButtonHeight)
    val padding = ButtonDefaults.ContentPadding

    when (state) {
        Review.CONFIRMED -> Button(onClick, sized, contentPadding = padding) { content() }

        Review.CARRIED -> FilledTonalButton(
            onClick,
            sized,
            contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) { content() }

        Review.OPEN -> OutlinedButton(onClick, sized, contentPadding = padding) { content() }
    }
}
