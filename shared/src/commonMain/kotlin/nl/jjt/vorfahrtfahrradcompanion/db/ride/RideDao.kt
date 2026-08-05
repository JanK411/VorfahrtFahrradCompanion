package nl.jjt.vorfahrtfahrradcompanion.db.ride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A ride row with the segments hanging off it counted — the shape the list of rides is drawn from,
 * which no single table holds.
 */
data class RideWithSegments(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val name: String?,
    val uploadedAtEpochMs: Long?,
    val segments: Int,
)

@Dao
interface RideDao {
    @Insert
    suspend fun insert(entity: RideEntity)

    @Query("UPDATE rides SET endedAtEpochMs = :endedAtEpochMs, name = :name WHERE id = :id")
    suspend fun close(id: String, endedAtEpochMs: Long, name: String?)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Every ride, newest first — the order a rider looks for the one they just finished in. The count
     * is a sub-select rather than a join and a GROUP BY, so a ride with no segments still comes back,
     * counted as the zero it is.
     */
    @Query(
        "SELECT r.*, (SELECT COUNT(*) FROM observations o WHERE o.rideId = r.id) AS segments " +
                "FROM rides r ORDER BY r.startedAtEpochMs DESC",
    )
    fun withSegmentCounts(): Flow<List<RideWithSegments>>

    /** Set only once the server has confirmed it holds the ride — see [RideEntity]. */
    @Query("UPDATE rides SET uploadedAtEpochMs = :uploadedAtEpochMs WHERE id = :id")
    suspend fun markUploaded(id: String, uploadedAtEpochMs: Long)
}
