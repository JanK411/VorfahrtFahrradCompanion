package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.Observation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore

/**
 * Keeps segments in the observations table. The answers become a JSON column here rather than in
 * the recorder that produced them: the encoding is what this table happens to hold them as.
 */
class RoomObservationStore(private val dao: ObservationDao) : ObservationStore {

    override suspend fun insert(observation: Observation) = dao.insert(
        ObservationEntity(
            rideId = observation.rideId,
            startedAtEpochMs = observation.startedAt.toEpochMilliseconds(),
            startKind = observation.startKind,
            endedAtEpochMs = observation.endedAt.toEpochMilliseconds(),
            endKind = observation.endKind,
            valuesJson = Json.encodeToString(observation.answers.stored()),
        ),
    )

    override fun lastValues(): Flow<StoredAnswers?> =
        dao.lastValuesJson().map { json -> json?.let { Json.decodeFromString<StoredAnswers>(it) } }

    override suspend fun countForRide(rideId: Long): Int = dao.countForRide(rideId)
}
