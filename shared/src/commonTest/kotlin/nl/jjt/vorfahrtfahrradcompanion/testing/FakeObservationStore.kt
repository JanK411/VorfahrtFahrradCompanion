package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Answers
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import kotlin.time.Instant

/** Keeps stored segments in a list, so a test can say what was written rather than what was called. */
class FakeObservationStore : ObservationStore {
    data class Stored(
        val rideId: Long,
        val startedAt: Instant,
        val startKind: BoundaryKind,
        val endedAt: Instant,
        val endKind: BoundaryKind,
        val values: Answers,
    )

    val inserted = mutableListOf<Stored>()
    private val last = MutableStateFlow<StoredAnswers?>(null)

    override suspend fun insert(
        rideId: Long,
        startedAt: Instant,
        startKind: BoundaryKind,
        endedAt: Instant,
        endKind: BoundaryKind,
        values: Answers,
    ) {
        inserted += Stored(rideId, startedAt, startKind, endedAt, endKind, values)
        // Through the id-keyed form the real store writes, so reading back still has to be resolved
        // against a catalogue — which is the whole point of the seam.
        last.value = values.stored()
    }

    override suspend fun countForRide(rideId: Long) = inserted.count { it.rideId == rideId }

    override fun lastValues(): Flow<StoredAnswers?> = last
}
