package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.StoredObservation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.service.ride.RideApi

/** Records what reached the wire, and refuses whenever [failure] is set. */
class FakeRideApi : RideApi {
    data class Sent(val ride: RecordedRide, val observations: List<StoredObservation>)

    val sent = mutableListOf<Sent>()
    var failure: Exception? = null

    override suspend fun upload(ride: RecordedRide, observations: List<StoredObservation>) {
        failure?.let { throw it }
        sent += Sent(ride, observations)
    }
}
