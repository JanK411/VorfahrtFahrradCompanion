package nl.jjt.vorfahrtfahrradcompanion.domain.recording

import kotlin.time.Instant

/**
 * Where rides are kept. A ride is opened before the segments that point at it are recorded, so
 * [open] gives back the id they belong to.
 */
interface RideStore {

    /** Opens a ride and returns the id the segments recorded during it belong to. */
    suspend fun open(startedAt: Instant): Long

    suspend fun close(id: Long, endedAt: Instant, name: String?)

    suspend fun delete(id: Long)
}
