package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.BoundaryKind
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import kotlin.time.Instant

/** Keeps stored segments in a list, so a test can say what was written rather than what was called. */
class FakeObservationStore : ObservationStore {
    data class Stored(
        val rideId: Long,
        val startedAt: Instant,
        val startKind: BoundaryKind,
        val endedAt: Instant,
        val endKind: BoundaryKind,
        val values: Selections,
    )

    val inserted = mutableListOf<Stored>()
    private val last = MutableStateFlow<Selections?>(null)

    override suspend fun insert(
        rideId: Long,
        startedAt: Instant,
        startKind: BoundaryKind,
        endedAt: Instant,
        endKind: BoundaryKind,
        values: Selections,
    ) {
        inserted += Stored(rideId, startedAt, startKind, endedAt, endKind, values)
        last.value = values
    }

    override suspend fun countForRide(rideId: Long) = inserted.count { it.rideId == rideId }

    override fun lastValues(): Flow<Selections?> = last
}
