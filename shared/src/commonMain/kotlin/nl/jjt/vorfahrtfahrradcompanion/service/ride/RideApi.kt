package nl.jjt.vorfahrtfahrradcompanion.service.ride

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide

/** Puts a whole ride on the server. Throws unless it answered that it took it. */
interface RideApi {
    suspend fun upload(ride: RecordedRide, observations: List<StoredObservation>)
}
