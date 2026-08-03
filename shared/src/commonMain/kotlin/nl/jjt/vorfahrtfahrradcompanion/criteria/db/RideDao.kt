package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RideDao {
    @Insert
    suspend fun insert(entity: RideEntity): Long

    @Query("UPDATE rides SET endedAtEpochMs = :endedAtEpochMs, name = :name WHERE id = :id")
    suspend fun close(id: Long, endedAtEpochMs: Long, name: String?)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)
}
