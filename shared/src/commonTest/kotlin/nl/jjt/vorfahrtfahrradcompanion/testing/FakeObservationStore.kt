package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.Observation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore

/** Keeps stored segments in a list, so a test can say what was written rather than what was called. */
class FakeObservationStore : ObservationStore {

    val inserted = mutableListOf<Observation>()
    private val last = MutableStateFlow<StoredAnswers?>(null)

    override suspend fun insert(observation: Observation) {
        inserted += observation
        // Through the id-keyed form the real store writes, so reading back still has to be resolved
        // against a catalogue — which is the whole point of the seam.
        last.value = observation.answers.stored()
    }

    override suspend fun countForRide(rideId: String) = inserted.count { it.rideId == rideId }

    override fun lastValues(): Flow<StoredAnswers?> = last
}
