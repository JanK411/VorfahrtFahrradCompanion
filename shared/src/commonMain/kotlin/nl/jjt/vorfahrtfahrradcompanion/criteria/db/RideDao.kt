package nl.jjt.vorfahrtfahrradcompanion.criteria.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insert(entity: RideEntity): Long

    /**
     * The ride still running, or null while none is. At most one is ever open — a ride is only started
     * while none is — so the newest is the only one this can find.
     */
    @Query("SELECT * FROM rides WHERE endedAtEpochMs IS NULL ORDER BY id DESC LIMIT 1")
    fun observeOpen(): Flow<RideEntity?>

    /** The same row, read once — for a caller that needs the ride to hang a segment off right now. */
    @Query("SELECT * FROM rides WHERE endedAtEpochMs IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun open(): RideEntity?

    @Query("UPDATE rides SET endedAtEpochMs = :endedAtEpochMs, name = :name WHERE id = :id")
    suspend fun close(id: Long, endedAtEpochMs: Long, name: String?)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)
}
