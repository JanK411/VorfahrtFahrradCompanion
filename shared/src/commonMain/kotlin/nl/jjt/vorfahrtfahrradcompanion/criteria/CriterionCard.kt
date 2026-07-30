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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
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
        if (expanded) ExpandedCriterion(criterion, selected, review, onTapValue, onNext)
        else CollapsedCriterion(criterion, selected, review)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedCriterion(
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
            when {
                review == Review.CARRIED -> "tap to keep"
                criterion.kind == CriterionKind.SINGLE -> "pick one"
                else -> "pick any"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (review == Review.CARRIED) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (criterion.kind == CriterionKind.MULTI) {
        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight),
        ) {
            Text("Next", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CollapsedCriterion(criterion: Criterion, selected: Set<String>, review: Review) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Carried-over values are greyed: they are still only a suggestion from the previous segment.
    val valueColor = when (review) {
        Review.CONFIRMED -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
        Text(
            criterion.label(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            selected.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
        )
    }

    when (review) {
        Review.CONFIRMED -> Icon(
            Icons.Filled.Check,
            contentDescription = "confirmed",
            tint = MaterialTheme.colorScheme.primary,
        )

        Review.CARRIED -> Text(
            "keep?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Review.OPEN -> Unit
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
