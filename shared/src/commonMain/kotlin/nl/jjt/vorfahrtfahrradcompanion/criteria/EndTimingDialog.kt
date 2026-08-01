package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What is asked of the rider the moment they press End: how well that press hit the boundary they
 * meant. It decides where — and whether — the stretch is recorded, so it is put before everything else
 * an end asks about.
 *
 * The same three answers live on the button itself, one hold and a slide away, which skips this
 * dialog entirely; the hint and the [BoundaryHelpDialog] behind the (?) are what lead a rider there.
 */
@Composable
internal fun EndTimingDialog(
    action: SegmentAction,
    onAnswer: (EndTiming) -> Unit,
    onDismiss: () -> Unit,
) {
    var explaining by remember { mutableStateOf(false) }
    if (explaining) BoundaryHelpDialog(onDismiss = { explaining = false })

    val grace = LateEndGrace.inWholeSeconds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (action) {
                        SegmentAction.STOP -> "Ending here"
                        SegmentAction.START_NEXT -> "Ending here, next one starts"
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinedIconButton(onClick = { explaining = true }, modifier = Modifier.size(44.dp)) {
                    Text("?", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
        text = {
            Text(
                "How well did you catch the moment? Next time you can say so without this dialog: hold " +
                    "the button instead of tapping it, then slide onto one of the three.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        // One column rather than the buttons row: side by side, three answers are too narrow to hit.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TimingOption(
                    title = "I hit precisely at the correct moment",
                    subtitle = "the end is stored where you pressed",
                    colors = ButtonDefaults.buttonColors(),
                    onClick = { onAnswer(EndTiming.EXACT) },
                )
                TimingOption(
                    title = "I was ~$grace seconds late",
                    subtitle = "the end is stored $grace seconds before the press",
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    onClick = { onAnswer(EndTiming.JUST_NOW) },
                )
                TimingOption(
                    title = "I was more than $grace seconds late",
                    subtitle = "the segment is thrown away",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = { onAnswer(EndTiming.LONGER) },
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text("Keep recording", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    )
}

/** One answer, full width so it is a thumb-sized target, with what it does underneath it. */
@Composable
private fun TimingOption(
    title: String,
    subtitle: String,
    colors: ButtonColors,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    colors = colors,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** What the hold gesture is for, for a rider who has not met it before. */
@Composable
private fun BoundaryHelpDialog(onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Answering on the button") },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Every end asks how well you caught the moment, because how late you were decides " +
                    "whether the stretch is worth keeping. Tapping End or Start next raises the " +
                    "question; holding one of them answers it in the same movement.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Hold the button until it buzzes and the three answers fill the screen above your " +
                    "thumb, a third of it each. Without letting go, slide up: precisely just above the " +
                    "button, about ${LateEndGrace.inWholeSeconds} seconds late in the middle, discard " +
                    "at the top — which throws the segment away, since a stretch that ended somewhere " +
                    "unknown is worse than none. Let go on the one you want; let go without having slid " +
                    "anywhere and nothing happens.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Either way the boundary is stamped when you press, not when you answer, so taking your " +
                    "time over it costs the recording nothing.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "One boundary needs no answer at all. Where a stretch ends because a single thing " +
                    "about it changes, hold the strip down the right-hand edge of that criterion's " +
                    "folded card — the one with the three dots. Its values fill the screen, and " +
                    "letting go on one ends the segment here and opens the next one described " +
                    "exactly as this one was, but for that value. The rest of the card is left to " +
                    "tapping and to scrolling the list.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "A start is the same question asked in advance: \"Start precise\" marks the segment as " +
                    "beginning where you pressed, \"Start shallow\" as having begun some way before it, " +
                    "for a change of path you notice only after riding onto it. The recording line then " +
                    "says \"started earlier\".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    },
    confirmButton = { Button(onDismiss) { Text("Got it") } },
)
