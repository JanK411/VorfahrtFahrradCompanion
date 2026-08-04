package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import kotlinx.coroutines.flow.Flow
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import kotlin.time.Instant

/**
 * Where recorded segments are kept. The recorder hands over what the rider described and is told
 * nothing about rows, columns or encodings — a segment is a set of selections between two boundaries,
 * and how that becomes storage is not its business.
 */
interface ObservationStore {

    suspend fun insert(
        rideId: Long,
        startedAt: Instant,
        startKind: BoundaryKind,
        endedAt: Instant,
        endKind: BoundaryKind,
        values: Selections,
    )

    /** What the segment stored last was described with, or null while nothing has been stored yet. */
    fun lastValues(): Flow<Selections?>

    /** How many segments a ride holds — what its closing summary tells the rider it amounts to. */
    suspend fun countForRide(rideId: Long): Int
}
