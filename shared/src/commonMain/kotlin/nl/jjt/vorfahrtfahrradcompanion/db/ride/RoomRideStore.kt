package nl.jjt.vorfahrtfahrradcompanion.db.ride

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.RideStore
import kotlin.time.Instant

/** Keeps rides in the rides table. */
class RoomRideStore(private val dao: RideDao) : RideStore {

    override suspend fun open(startedAt: Instant): Long =
        dao.insert(RideEntity(startedAtEpochMs = startedAt.toEpochMilliseconds()))

    override suspend fun close(id: Long, endedAt: Instant, name: String?) =
        dao.close(id, endedAt.toEpochMilliseconds(), name)

    override suspend fun delete(id: Long) = dao.delete(id)
}
