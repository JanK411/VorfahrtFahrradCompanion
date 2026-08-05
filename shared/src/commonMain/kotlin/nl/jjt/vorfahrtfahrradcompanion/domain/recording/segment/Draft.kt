package nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Answers
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion

/**
 * What the rider has entered but not stored yet.
 *
 * [approved] holds the criteria the rider has stood by *for the current segment*. Answers outlive a
 * segment, this set does not: after an end every carried-over value is a suggestion again, and only what
 * the rider approves is stored. See `SegmentRecorder.tap`.
 */
data class Draft(
    val segment: Segment = Segment.Idle,
    val answers: Answers = Answers(),
    val approved: Set<Criterion> = emptySet(),
)
