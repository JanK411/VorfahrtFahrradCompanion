package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredAnswers
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.Observation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import kotlin.time.Instant

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

    override suspend fun countForRide(rideId: String): Int = dao.countForRide(rideId)

    override suspend fun forRide(rideId: String): List<StoredObservation> =
        dao.forRide(rideId).map { row ->
            StoredObservation(
                startedAt = Instant.fromEpochMilliseconds(row.startedAtEpochMs),
                startKind = row.startKind,
                endedAt = Instant.fromEpochMilliseconds(row.endedAtEpochMs),
                endKind = row.endKind,
                answers = Json.decodeFromString(row.valuesJson),
            )
        }
}
