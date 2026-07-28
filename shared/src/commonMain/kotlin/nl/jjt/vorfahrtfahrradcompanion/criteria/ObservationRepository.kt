package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import kotlin.time.Clock

/**
 * Persists observations locally instead of sending them to the server. Each observation stores only the
 * selected values and the timestamp of the moment it was recorded — the sampling location is recovered
 * later by matching that timestamp against the GPS track.
 */
class ObservationRepository(
    private val dao: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    suspend fun record(selections: Selections) = dao.insert(
        ObservationEntity(
            recordedAtEpochMs = clock.now().toEpochMilliseconds(),
            valuesJson = Json.encodeToString(selections.compact()),
        ),
    )
}
