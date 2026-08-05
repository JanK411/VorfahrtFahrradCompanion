package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import kotlin.time.Instant

/**
 * A segment read back out of storage, as against [Observation], which is one on its way in.
 *
 * The difference is the answers: they come back id-keyed, because the table has no record of what kind
 * of question each was. Resolving them against the catalogue is for showing them to a rider — anything
 * sending them on wants what was actually recorded, including ids the catalogue has since dropped.
 *
 * There is no `rideId`: these are only ever read a whole ride at a time.
 */
data class StoredObservation(
    val startedAt: Instant,
    val startKind: BoundaryKind,
    val endedAt: Instant,
    val endKind: BoundaryKind,
    val answers: StoredAnswers,
)
