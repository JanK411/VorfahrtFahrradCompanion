package nl.jjt.vorfahrtfahrradcompanion.db.ride

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Keeps rides in the rides table. */
class RoomRideStore(private val dao: RideDao) : RideStore {

    /** The id is minted here, where the row is written — see [RideEntity]. */
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun open(startedAt: Instant): String {
        val id = Uuid.random().toString()
        dao.insert(RideEntity(id = id, startedAtEpochMs = startedAt.toEpochMilliseconds()))
        return id
    }

    override suspend fun close(id: String, endedAt: Instant, name: String?) =
        dao.close(id, endedAt.toEpochMilliseconds(), name)

    override suspend fun delete(id: String) = dao.delete(id)
}
