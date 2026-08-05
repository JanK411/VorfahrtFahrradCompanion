package nl.jjt.vorfahrtfahrradcompanion.service.ride

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Answers
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.Observation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideState
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeClock
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeObservationStore
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeRideApi
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeRideStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-08-05T14:02:11Z")
private val endedAt = Instant.parse("2026-08-05T14:40:53Z")
private val sentAt = Instant.parse("2026-08-05T15:10:00Z")

private val width = Criterion("WIDTH", CriterionKind.SINGLE)

class RideUploaderTest {

    private val api = FakeRideApi()
    private val observations = FakeObservationStore()
    private val rides = FakeRideStore()
    private val clock = FakeClock(sentAt)
    private val uploader = SendingRideUploader(api, observations, rides, clock)

    private suspend fun aFinishedRide(): RecordedRide {
        val id = rides.open(startedAt)
        rides.close(id, endedAt, "Kanaalweg noord")
        observations.insert(
            Observation(
                rideId = id,
                startedAt = startedAt,
                startKind = BoundaryKind.EXACT,
                endedAt = endedAt,
                endKind = BoundaryKind.EXACT,
                answers = Answers(mapOf(width to setOf(CriterionValue("W_2")))),
            ),
        )
        return rides.recorded().first().single()
    }

    @Test
    fun aSentRideIsMarkedUploadedAtTheMomentItWasTaken() = runTest {
        val ride = aFinishedRide()

        val outcome = uploader.upload(ride)

        assertTrue(outcome.isSuccess)
        assertEquals(sentAt, rides.rows.single().uploadedAt)
        assertEquals(RideState.UPLOADED, rides.recorded().first().single().state)
    }

    @Test
    fun theRideGoesOutWithTheObservationsRecordedDuringIt() = runTest {
        val ride = aFinishedRide()

        uploader.upload(ride)

        val sent = api.sent.single()
        assertEquals(ride.id, sent.ride.id)
        assertEquals(1, sent.observations.size)
        assertEquals(setOf(CriterionValue("W_2")), sent.observations.single().answers["WIDTH"])
    }

    /**
     * The whole reason the timestamp is written after the answer rather than before it: a ride the
     * server never confirmed has to stay indistinguishable from one never sent, or pressing send again
     * would be a lie.
     */
    @Test
    fun aRefusedRideIsLeftExactlyAsItWas() = runTest {
        val ride = aFinishedRide()
        api.failure = IllegalStateException("Server answered 500 Internal Server Error")

        val outcome = uploader.upload(ride)

        assertTrue(outcome.isFailure)
        assertEquals(
            "Server answered 500 Internal Server Error",
            outcome.exceptionOrNull()?.message,
        )
        assertNull(rides.rows.single().uploadedAt)
        assertEquals(RideState.FINISHED, rides.recorded().first().single().state)
    }

    @Test
    fun sendingAgainMovesTheMomentItWasTakenForward() = runTest {
        val ride = aFinishedRide()
        uploader.upload(ride)

        clock += 2.hours
        uploader.upload(rides.recorded().first().single())

        assertEquals(sentAt + 2.hours, rides.rows.single().uploadedAt)
        assertEquals(2, api.sent.size)
    }

    /** The same id both times, so the server can tell it is the ride it already holds. */
    @Test
    fun sendingAgainSendsTheSameRideId() = runTest {
        val ride = aFinishedRide()

        uploader.upload(ride)
        uploader.upload(rides.recorded().first().single())

        assertEquals(listOf(ride.id, ride.id), api.sent.map { it.ride.id })
    }
}
