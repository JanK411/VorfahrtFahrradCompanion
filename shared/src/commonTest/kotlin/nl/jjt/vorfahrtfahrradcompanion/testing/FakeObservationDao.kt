package nl.jjt.vorfahrtfahrradcompanion.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.jjt.vorfahrtfahrradcompanion.db.observation.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.db.observation.ObservationEntity

/** Keeps observation rows in a list, so a test can read the columns that were written. */
class FakeObservationDao : ObservationDao {
    val rows = mutableListOf<ObservationEntity>()
    private val last = MutableStateFlow<String?>(null)

    override suspend fun insert(entity: ObservationEntity) {
        rows += entity
        last.value = entity.valuesJson
    }

    override suspend fun countForRide(rideId: String) = rows.count { it.rideId == rideId }

    override fun lastValuesJson(): Flow<String?> = last
}
