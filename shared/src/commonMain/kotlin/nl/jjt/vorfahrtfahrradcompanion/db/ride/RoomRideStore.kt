package nl.jjt.vorfahrtfahrradcompanion.db.ride

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Keeps rides in the rides table. */
class RoomRideStore(private val dao: RideDao) : RideStore {

    /** The id is minted here, where the row is written — see [RideEntity]. */
    override suspend fun open(startedAt: Instant): String {
        val id = Uuid.random().toString()
        dao.insert(RideEntity(id = id, startedAtEpochMs = startedAt.toEpochMilliseconds()))
        return id
    }

    override suspend fun close(id: String, endedAt: Instant, name: String?) =
        dao.close(id, endedAt.toEpochMilliseconds(), name)

    override suspend fun delete(id: String) = dao.delete(id)

    override fun recorded(): Flow<List<RecordedRide>> =
        dao.withSegmentCounts().map { rows -> rows.map(RideWithSegments::toDomain) }

    override suspend fun markUploaded(id: String, at: Instant) =
        dao.markUploaded(id, at.toEpochMilliseconds())
}

/** Epoch millis become instants here; which state the ride is in is the domain's to work out. */
private fun RideWithSegments.toDomain() = RecordedRide(
    id = id,
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    endedAt = endedAtEpochMs?.let(Instant::fromEpochMilliseconds),
    name = name,
    segments = segments,
    uploadedAt = uploadedAtEpochMs?.let(Instant::fromEpochMilliseconds),
)
