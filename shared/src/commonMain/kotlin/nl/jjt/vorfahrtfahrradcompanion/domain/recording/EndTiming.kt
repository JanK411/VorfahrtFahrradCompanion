package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import kotlin.time.Duration.Companion.seconds

/**
 * How well the press hit the end the rider meant — asked on every end, because an end is worth keeping
 * only if they can still say where it was. [PRECISE] takes the press for the boundary, [SLIGHTLY_LATE]
 * puts it [LateEndGrace] back, and [TOO_LATE] means the stretch ended somewhere between there and here,
 * which is no place to record.
 */
enum class EndTiming {
    PRECISE,
    SLIGHTLY_LATE,
    TOO_LATE;

    /** How the boundary is stored: anything but [PRECISE] is an end marked after the fact. */
    val boundary: BoundaryKind get() = if (this == PRECISE) BoundaryKind.EXACT else BoundaryKind.EARLIER
}

/** How far back an end marked [EndTiming.SLIGHTLY_LATE] is taken to be. */
val LateEndGrace = 10.seconds
