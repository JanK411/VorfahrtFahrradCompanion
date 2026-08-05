package nl.jjt.vorfahrtfahrradcompanion.service.ride

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide

/**
 * Hands a whole ride to the server and reports whether it got there. Failure is an ordinary answer
 * rather than an exception: a rider out of signal is the common case, not an exceptional one.
 *
 * Nothing about the stored ride changes unless the server confirmed it — see [RideUploader.upload].
 */
interface RideUploader {

    /** Sends [ride] and everything recorded during it. Marks it uploaded only on a positive answer. */
    suspend fun upload(ride: RecordedRide): Result<Unit>
}

/**
 * Stands in until the endpoint exists. Answers with a failure rather than throwing, so the screen shows
 * the same message it will show for a real unreachable server — which is the path being built now.
 */
class UnsentRideUploader : RideUploader {
    override suspend fun upload(ride: RecordedRide): Result<Unit> =
        Result.failure(IllegalStateException("Sending rides is not built yet"))
}
