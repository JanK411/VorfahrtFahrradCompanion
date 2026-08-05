package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.service.ride.RideUploader

/** Records what was asked to be sent, and answers however the test tells it to. */
class FakeRideUploader : RideUploader {
    val uploaded = mutableListOf<RecordedRide>()
    var outcome: Result<Unit> = Result.success(Unit)

    override suspend fun upload(ride: RecordedRide): Result<Unit> {
        uploaded += ride
        return outcome
    }
}
