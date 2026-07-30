package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlin.time.Duration.Companion.seconds

/**
 * Where the boundary of a segment really lies. A rider cannot always press the button at the exact
 * moment the path changes, so [EARLIER] records that the segment already started (or ended) some time
 * before the timestamp that was captured.
 */
enum class BoundaryKind { EXACT, EARLIER }

/**
 * How far past the boundary an end was marked, answered on the button itself. An end is worth keeping
 * only if the rider can still say where it was: [JUST_NOW] puts it [MissedEndGrace] back, while
 * [LONGER] means the stretch ended somewhere between there and here, which is no place to record.
 */
enum class MissedEnd { JUST_NOW, LONGER }

/** How far back an end marked [MissedEnd.JUST_NOW] is taken to be. */
val MissedEndGrace = 10.seconds
