package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import kotlinx.coroutines.flow.Flow
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers

/**
 * Where recorded segments are kept. The recorder hands over what the rider described and is told
 * nothing about rows, columns or encodings — a segment is a set of answers between two boundaries,
 * and how that becomes storage is not its business.
 */
interface ObservationStore {

    suspend fun insert(observation: Observation)

    /**
     * What the segment stored last was described with, or null while nothing has been stored yet.
     * By id: what came back out of storage predates the catalogue now in hand, so it takes a
     * [nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue] to say which criteria those are.
     */
    fun lastValues(): Flow<StoredAnswers?>

    /** How many segments a ride holds — what its closing summary tells the rider it amounts to. */
    suspend fun countForRide(rideId: Long): Int
}
