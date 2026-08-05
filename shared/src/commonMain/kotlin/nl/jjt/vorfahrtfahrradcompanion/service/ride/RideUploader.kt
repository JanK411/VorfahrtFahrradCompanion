package nl.jjt.vorfahrtfahrradcompanion.service.ride

import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ObservationStore
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * Hands a whole ride to the server and reports whether it got there. Failure is an ordinary answer
 * rather than an exception: a rider out of signal is the common case, not an exceptional one.
 */
interface RideUploader {

    /** Sends [ride] and everything recorded during it. */
    suspend fun upload(ride: RecordedRide): Result<Unit>
}

/**
 * Collects the ride's observations, posts them, and records the send **only** once the server has
 * confirmed it. Nothing about the stored ride changes on a failure — which is what makes a ride that
 * timed out halfway indistinguishable from one never sent, and pressing send again safe.
 *
 * This is where a thrown failure becomes a returned one, and [RideApi] keeps throwing rather than
 * answering with a `Result` of its own. Reading the observations and marking the ride can fail too,
 * and all three failures mean the same thing to the rider, so one catch covers them — leaving a
 * single place that has to remember to let a cancellation through.
 */
class SendingRideUploader(
    private val api: RideApi,
    private val observations: ObservationStore,
    private val rides: RideStore,
    private val clock: Clock = Clock.System,
) : RideUploader {

    override suspend fun upload(ride: RecordedRide): Result<Unit> = try {
        api.upload(ride, observations.forRide(ride.id))
        rides.markUploaded(ride.id, clock.now())
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
