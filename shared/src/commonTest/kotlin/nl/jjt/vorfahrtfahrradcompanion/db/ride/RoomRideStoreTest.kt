package nl.jjt.vorfahrtfahrradcompanion.db.ride

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideState
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeRideDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-08-05T14:02:11Z")
private val endedAt = Instant.parse("2026-08-05T14:40:53Z")
private val sentAt = Instant.parse("2026-08-05T15:10:00Z")

class RoomRideStoreTest {

    private val dao = FakeRideDao()
    private val store = RoomRideStore(dao)

    /**
     * The id is what the server will know the ride by, so it has to be minted here rather than left to
     * the table — and has to differ per ride, which a row number would also manage but only on one device.
     */
    @Test
    fun openingARideMintsAnIdOfItsOwn() = runTest {
        val first = store.open(startedAt)
        val second = store.open(startedAt)

        assertNotEquals(first, second)
        assertTrue(first.isNotEmpty())
        assertEquals(listOf(first, second), dao.entities.map { it.id })
    }

    @Test
    fun aRideStillBeingRiddenIsOpen() = runTest {
        store.open(startedAt)

        val ride = store.recorded().first().single()

        assertEquals(RideState.OPEN, ride.state)
        assertNull(ride.endedAt)
    }

    @Test
    fun aClosedRideIsFinishedUntilItHasBeenSent() = runTest {
        val id = store.open(startedAt)
        store.close(id, endedAt, "Kanaalweg noord")

        val ride = store.recorded().first().single()

        assertEquals(RideState.FINISHED, ride.state)
        assertEquals(endedAt, ride.endedAt)
        assertEquals("Kanaalweg noord", ride.name)
        assertNull(ride.uploadedAt)
    }

    @Test
    fun aRideTheServerTookIsUploaded() = runTest {
        val id = store.open(startedAt)
        store.close(id, endedAt, null)
        store.markUploaded(id, sentAt)

        val ride = store.recorded().first().single()

        assertEquals(RideState.UPLOADED, ride.state)
        assertEquals(sentAt, ride.uploadedAt)
    }

    /** Nothing to send while it is still being ridden, whatever else happens to be true of the row. */
    @Test
    fun anUnfinishedRideStaysOpenEvenOnceMarkedUploaded() = runTest {
        val id = store.open(startedAt)
        store.markUploaded(id, sentAt)

        assertEquals(RideState.OPEN, store.recorded().first().single().state)
    }

    @Test
    fun aRideCarriesTheCountOfItsSegments() = runTest {
        val id = store.open(startedAt)
        dao.segmentsPerRide = mapOf(id to 12)

        assertEquals(12, store.recorded().first().single().segments)
    }

    @Test
    fun aRideWithNoSegmentsIsCountedAsNone() = runTest {
        store.open(startedAt)

        assertEquals(0, store.recorded().first().single().segments)
    }

    /** Newest first — the rider is looking for the one they just got off. */
    @Test
    fun ridesComeBackNewestFirst() = runTest {
        val older = store.open(startedAt)
        val newer = store.open(startedAt + kotlin.time.Duration.parse("1h"))

        assertEquals(listOf(newer, older), store.recorded().first().map { it.id })
    }

    @Test
    fun aDeletedRideIsGoneFromTheList() = runTest {
        val id = store.open(startedAt)
        store.delete(id)

        assertTrue(store.recorded().first().isEmpty())
    }
}
