package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ObservationDao {
    @Insert
    suspend fun insert(entity: ObservationEntity)

    /** How many segments a ride holds — what its closing summary tells the rider it amounts to. */
    @Query("SELECT COUNT(*) FROM observations WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
}
