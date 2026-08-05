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

    /**
     * A row that will not parse counts as no row at all. What this offers is a convenience — the
     * answers a fresh segment is filled in from — and starting one empty beats an unreadable row
     * throwing into a flow nobody collects with a catch, which takes the criteria screen down
     * mid-ride. [forRide] is the opposite case and treats the same row differently.
     */
    override fun lastValues(): Flow<StoredAnswers?> =
        dao.lastValuesJson().map { json -> json?.let(::decodeOrNull) }

    override suspend fun countForRide(rideId: String): Int = dao.countForRide(rideId)

    /**
     * Strict where [lastValues] is forgiving: a row that will not parse fails the whole read, rather
     * than letting a ride go to the server with a segment quietly missing from it.
     */
    override suspend fun forRide(rideId: String): List<StoredObservation> =
        dao.forRide(rideId).map { row ->
            StoredObservation(
                startedAt = Instant.fromEpochMilliseconds(row.startedAtEpochMs),
                startKind = row.startKind,
                endedAt = Instant.fromEpochMilliseconds(row.endedAtEpochMs),
                endKind = row.endKind,
                answers = Json.decodeFromString<StoredAnswers>(row.valuesJson),
            )
        }

    private fun decodeOrNull(json: String): StoredAnswers? =
        runCatching { Json.decodeFromString<StoredAnswers>(json) }.getOrNull()
}
