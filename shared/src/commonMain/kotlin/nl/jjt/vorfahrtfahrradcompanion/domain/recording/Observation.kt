package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Answers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import kotlin.time.Instant

/**
 * One described stretch of path: what the rider answered between two boundaries, and which ride it
 * belongs to. Neither half of the recording owns it alone — the ride supplies [rideId], the segment
 * supplies its boundaries — which is why it sits here rather than under either.
 *
 * [answers] holds only what the rider approved for this stretch; carrying a value over to the next
 * segment makes it a suggestion again, not part of this observation. See `SegmentRecorder.end`.
 */
data class Observation(
    val rideId: Long,
    val startedAt: Instant,
    val startKind: BoundaryKind,
    val endedAt: Instant,
    val endKind: BoundaryKind,
    val answers: Answers,
)
