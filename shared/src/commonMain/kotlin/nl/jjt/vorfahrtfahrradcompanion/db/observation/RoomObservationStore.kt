package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredSelections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import kotlin.time.Instant

/**
 * Keeps segments in the observations table. The selections become a JSON column here rather than in
 * the recorder that produced them: the encoding is what this table happens to hold them as.
 */
class RoomObservationStore(private val dao: ObservationDao) : ObservationStore {

    override suspend fun insert(
        rideId: Long,
        startedAt: Instant,
        startKind: BoundaryKind,
        endedAt: Instant,
        endKind: BoundaryKind,
        values: Selections,
    ) = dao.insert(
        ObservationEntity(
            rideId = rideId,
            startedAtEpochMs = startedAt.toEpochMilliseconds(),
            startKind = startKind,
            endedAtEpochMs = endedAt.toEpochMilliseconds(),
            endKind = endKind,
            valuesJson = Json.encodeToString(values.stored()),
        ),
    )

    override fun lastValues(): Flow<StoredSelections?> =
        dao.lastValuesJson().map { json -> json?.let { Json.decodeFromString<StoredSelections>(it) } }

    override suspend fun countForRide(rideId: Long): Int = dao.countForRide(rideId)
}
