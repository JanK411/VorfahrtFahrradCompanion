package nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Where rides are kept. A ride is opened before the segments that point at it are recorded, so
 * [open] gives back the id they belong to.
 */
interface RideStore {

    /** Opens a ride and returns the id the segments recorded during it belong to. */
    suspend fun open(startedAt: Instant): String

    suspend fun close(id: String, endedAt: Instant, name: String?)

    suspend fun delete(id: String)

    /** Every ride recorded so far, newest first — what the rider looks back on and sends from. */
    fun recorded(): Flow<List<RecordedRide>>

    /** Records that the server has taken the ride. Only ever called once it actually confirmed so. */
    suspend fun markUploaded(id: String, at: Instant)
}
