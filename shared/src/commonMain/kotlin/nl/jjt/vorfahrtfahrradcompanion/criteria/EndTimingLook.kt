package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * How the three answers to "how well did you catch the moment?" look.
 *
 * They are asked in two places — the dialog a tap raises and the menu a hold opens — and a rider who
 * learns one has to recognise the other at a glance, so both read their words, marks and colours off
 * here rather than each spelling out its own.
 */
internal val EndTiming.title: String
    get() = when (this) {
        EndTiming.PRECISE -> "Precisely"
        EndTiming.SLIGHTLY_LATE -> "~${LateEndGrace.inWholeSeconds} s late"
        EndTiming.TOO_LATE -> "Too late"
    }

internal val EndTiming.icon: ImageVector
    get() = when (this) {
        EndTiming.PRECISE -> Icons.Filled.Check
        // The end goes back, which is what the arrow says — the same mark "Start earlier" carries.
        EndTiming.SLIGHTLY_LATE -> Icons.AutoMirrored.Filled.ArrowBack
        EndTiming.TOO_LATE -> Icons.Filled.Delete
    }

/** Green through amber to red: a clean end, a slightly late one, and one not worth keeping at all. */
internal val EndTiming.color: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        EndTiming.PRECISE -> MaterialTheme.colorScheme.primary
        EndTiming.SLIGHTLY_LATE -> MaterialTheme.colorScheme.tertiary
        EndTiming.TOO_LATE -> MaterialTheme.colorScheme.error
    }

internal val EndTiming.onColor: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        EndTiming.PRECISE -> MaterialTheme.colorScheme.onPrimary
        EndTiming.SLIGHTLY_LATE -> MaterialTheme.colorScheme.onTertiary
        EndTiming.TOO_LATE -> MaterialTheme.colorScheme.onError
    }

/** What each answer does to the recording, spelled out where there is room to spell it out. */
internal val EndTiming.effect: String
    get() = when (this) {
        EndTiming.PRECISE -> "the end is stored where you pressed"
        EndTiming.SLIGHTLY_LATE ->
            "the end is stored ${LateEndGrace.inWholeSeconds} seconds before the press"

        EndTiming.TOO_LATE -> "the segment is thrown away"
    }
