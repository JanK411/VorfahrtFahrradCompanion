package nl.jjt.vorfahrtfahrradcompanion.ui.criteria

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.EndTiming
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.SegmentAction

/**
 * What is asked of the rider the moment they press End: how well that press hit the boundary they
 * meant. It decides where — and whether — the stretch is recorded, so it is put before everything else
 * an end asks about.
 *
 * The same three answers, under the same three labels, live on the button itself one hold and a slide
 * away, which skips this dialog entirely; the hint and the [BoundaryHelpDialog] behind the (?) are
 * what lead a rider there.
 */
@Composable
internal fun EndTimingDialog(
    action: SegmentAction,
    onAnswer: (EndTiming) -> Unit,
    onDismiss: () -> Unit,
) {
    var explaining by remember { mutableStateOf(false) }
    if (explaining) BoundaryHelpDialog(onDismiss = { explaining = false })

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
                "How well did you catch the moment? Next time you can answer without this dialog: " +
                        "hold the button instead of tapping it, then slide onto one of the three.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        // One column rather than the buttons row: side by side, three answers are too narrow to hit.
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Top to bottom in the order the hold menu stacks them bottom to top: the answer a
                // rider reaches for most is the one nearest their thumb either way.
                EndTiming.entries.forEach { timing -> TimingOption(timing) { onAnswer(timing) } }

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
private fun TimingOption(timing: EndTiming, onClick: () -> Unit) = Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = timing.color,
        contentColor = timing.onColor,
    ),
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(timing.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(timing.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Text(timing.effect, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
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
                        "thumb, a third of it each. Without letting go, slide up: " +
                        "\"${EndTiming.PRECISE.title}\" just above the button, " +
                        "\"${EndTiming.SLIGHTLY_LATE.title}\" in the middle, " +
                        "\"${EndTiming.TOO_LATE.title}\" on top — which throws the segment away, since a " +
                        "stretch that ended somewhere unknown is worse than none. Let go on the one you " +
                        "want; let go without having slid anywhere and nothing happens.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Either way the boundary is stamped when you press, not when you answer, so taking your " +
                        "time over it costs the recording nothing.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "A start is the same question asked in advance: \"Start precise\" marks the segment as " +
                        "beginning where you pressed, \"Start earlier\" as having begun some way before it, " +
                        "for a change of path you notice only after riding onto it. The recording line then " +
                        "says \"started earlier\".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    },
    confirmButton = { Button(onDismiss) { Text("Got it") } },
)
