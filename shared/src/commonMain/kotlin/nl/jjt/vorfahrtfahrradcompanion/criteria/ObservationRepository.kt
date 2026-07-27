package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import kotlin.time.Clock

/**
 * Persists observations locally instead of sending them to the server. Each observation stores only the
 * selected [values] and the timestamp of the moment it was recorded — the sampling location is recovered
 * later by matching that timestamp against the GPS track.
 */
class ObservationRepository(
    private val dao: ObservationDao,
    private val clock: Clock = Clock.System,
) {
    private val valuesSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    suspend fun record(values: Map<String, Set<String>>) = dao.insert(
        ObservationEntity(
            recordedAtEpochMs = clock.now().toEpochMilliseconds(),
            valuesJson = Json.encodeToString(valuesSerializer, values.mapValues { it.value.toList() }),
        ),
    )
}
