package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Insert
    suspend fun insert(entity: ObservationEntity)

    /**
     * The values of the segment stored last, or null while nothing has been stored — what a fresh
     * segment offers to be filled in from. Ordered by id rather than by the boundary, because a
     * boundary moved back for a late press can land before the one stored before it.
     */
    @Query("SELECT valuesJson FROM observations ORDER BY id DESC LIMIT 1")
    fun lastValuesJson(): Flow<String?>
}
