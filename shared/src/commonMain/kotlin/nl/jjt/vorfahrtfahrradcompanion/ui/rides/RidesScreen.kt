package nl.jjt.vorfahrtfahrradcompanion.ui.rides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideState
import nl.jjt.vorfahrtfahrradcompanion.ui.common.timeOfDay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Instant

/** Everything recorded so far, newest first — where a ride is looked back on and sent from. */
@Composable
fun RidesScreen(modifier: Modifier = Modifier) {
    val viewModel: RidesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.rides.isEmpty()) {
        NoRidesYet(modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.rides, key = RecordedRide::id) { ride ->
            RideRow(ride)
            HorizontalDivider()
        }
    }
}

@Composable
private fun NoRidesYet(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No rides recorded yet.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RideRow(ride: RecordedRide) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(ride.title(), style = MaterialTheme.typography.titleMedium)
            Text(
                ride.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StateBadge(ride.state)
    }
}

@Composable
private fun StateBadge(state: RideState) {
    val (label, background) = when (state) {
        RideState.OPEN -> "open" to MaterialTheme.colorScheme.tertiaryContainer
        RideState.FINISHED -> "not sent" to MaterialTheme.colorScheme.secondaryContainer
        RideState.UPLOADED -> "sent" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = background, shape = MaterialTheme.shapes.small, contentColor = Color.Unspecified) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** What the rider called it, or the day it happened when they did not bother. */
private fun RecordedRide.title(): String = name ?: startedAt.asDate()

/** When it started, how long it ran, and the only measure of a ride there is — how much it describes. */
private fun RecordedRide.subtitle(): String {
    val length = endedAt?.let { (it - startedAt).asRideLength() } ?: "still riding"
    return "${startedAt.timeOfDay()} · $length · $segments ${if (segments == 1) "segment" else "segments"}"
}

private val MONTHS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun Instant.asDate(): String {
    val date = toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.day} ${MONTHS[date.month.ordinal]}"
}

/** Minutes up to an hour, then `1 h 04` — a ride is read at a glance, not measured. */
private fun Duration.asRideLength(): String {
    val hours = inWholeHours
    val minutes = inWholeMinutes % 60
    return if (hours > 0) "$hours h ${minutes.toString().padStart(2, '0')}" else "$minutes min"
}
