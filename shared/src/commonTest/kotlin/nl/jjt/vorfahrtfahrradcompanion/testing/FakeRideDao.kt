package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideDao
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideEntity
import nl.jjt.vorfahrtfahrradcompanion.db.ride.RideWithSegments

/**
 * Keeps ride rows in a list, so a test can read the columns that were written. [segmentsPerRide] stands
 * in for the sub-select the real query counts with — tests set it rather than inserting observations.
 */
class FakeRideDao : RideDao {
    private val rows = MutableStateFlow<List<RideEntity>>(emptyList())
    var segmentsPerRide: Map<String, Int> = emptyMap()

    val entities: List<RideEntity> get() = rows.value

    override suspend fun insert(entity: RideEntity) {
        rows.value += entity
    }

    override suspend fun close(id: String, endedAtEpochMs: Long, name: String?) = update(id) {
        it.copy(endedAtEpochMs = endedAtEpochMs, name = name)
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override fun withSegmentCounts(): Flow<List<RideWithSegments>> = rows.map { entities ->
        entities.sortedByDescending(RideEntity::startedAtEpochMs).map {
            RideWithSegments(
                id = it.id,
                startedAtEpochMs = it.startedAtEpochMs,
                endedAtEpochMs = it.endedAtEpochMs,
                name = it.name,
                uploadedAtEpochMs = it.uploadedAtEpochMs,
                segments = segmentsPerRide[it.id] ?: 0,
            )
        }
    }

    override suspend fun markUploaded(id: String, uploadedAtEpochMs: Long) = update(id) {
        it.copy(uploadedAtEpochMs = uploadedAtEpochMs)
    }

    private fun update(id: String, change: (RideEntity) -> RideEntity) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}
