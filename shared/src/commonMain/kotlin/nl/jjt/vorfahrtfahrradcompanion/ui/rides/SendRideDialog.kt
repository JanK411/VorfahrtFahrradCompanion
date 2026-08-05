package nl.jjt.vorfahrtfahrradcompanion.ui.rides

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import nl.jjt.vorfahrtfahrradcompanion.ui.common.timeOfDay

/**
 * The question a tapped ride raises, and nothing more: a ride only leaves the device once the rider has
 * said so, and one already on the server is sent again only deliberately.
 */
@Composable
internal fun SendRideDialog(
    prompt: RidePrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ride = prompt.ride
    val what = ride.name ?: ride.startedAt.asDate()

    when (prompt) {
        is RidePrompt.StillOpen -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Still riding") },
            text = { Text("\"$what\" has not been ended yet, so there is nothing to send.") },
            confirmButton = { TextButton(onDismiss) { Text("OK") } },
        )

        is RidePrompt.Send -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send ride") },
            text = { Text("Send \"$what\" and its ${ride.segments.segments()} to the server?") },
            confirmButton = { TextButton(onConfirm) { Text("Send") } },
            dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        )

        is RidePrompt.SendAgain -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send again") },
            text = {
                val sent = ride.uploadedAt
                Text(
                    "\"$what\" was already sent" +
                            (sent?.let { " on ${it.asDate()} at ${it.timeOfDay()}" } ?: "") +
                            ". Send it again?",
                )
            },
            confirmButton = { TextButton(onConfirm) { Text("Send again") } },
            dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        )
    }
}

private fun Int.segments() = if (this == 1) "1 segment" else "$this segments"
