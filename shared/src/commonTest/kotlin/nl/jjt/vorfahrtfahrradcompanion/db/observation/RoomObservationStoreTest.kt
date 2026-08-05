package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.*
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.Observation
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeObservationDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-07-20T12:43:37Z")
private val endedAt = Instant.parse("2026-07-20T12:46:37Z")
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)
private val values = Answers(mapOf(users to setOf(CriterionValue("CARS"))))

private fun observation(
    rideId: String,
    startKind: BoundaryKind = BoundaryKind.EXACT,
    endKind: BoundaryKind = BoundaryKind.EXACT,
) = Observation(rideId, startedAt, startKind, endedAt, endKind, values)

class RoomObservationStoreTest {

    private val dao = FakeObservationDao()
    private val store = RoomObservationStore(dao)

    @Test
    fun aSegmentBecomesARowWithBothBoundariesInEpochMillis() = runTest {
        store.insert(observation(rideId = "ride-7", startKind = BoundaryKind.EXACT, endKind = BoundaryKind.EARLIER))

        val row = dao.rows.single()
        assertEquals("ride-7", row.rideId)
        assertEquals(startedAt.toEpochMilliseconds(), row.startedAtEpochMs)
        assertEquals(BoundaryKind.EXACT, row.startKind)
        assertEquals(endedAt.toEpochMilliseconds(), row.endedAtEpochMs)
        assertEquals(BoundaryKind.EARLIER, row.endKind)
    }

    /**
     * The column format, which the rows on an installed device are already written in. It is not an
     * implementation detail of this class: changing it needs a migration, so it is pinned here.
     */
    @Test
    fun theAnswersAreStoredAsJsonKeyedByCriterionId() = runTest {
        store.insert(observation(rideId = "ride-1"))

        assertEquals("""{"ALLOWED_USERS":["CARS"]}""", dao.rows.single().valuesJson)
    }

    /**
     * What comes back is keyed by id — the table has no record of what kind of question each was —
     * so it takes a catalogue to become [Answers] again.
     */
    @Test
    fun theLastStoredAnswersAreReadBackWhole() = runTest {
        store.insert(observation(rideId = "ride-1"))

        val read = store.lastValues().first()

        assertEquals(values.stored(), read)
        assertEquals(values, Catalogue(mapOf(users to listOf(CriterionValue("CARS")))).resolve(read!!))
    }

    @Test
    fun withNothingStoredThereIsNoLastValue() = runTest {
        assertNull(store.lastValues().first())
    }

    @Test
    fun segmentsAreCountedPerRide() = runTest {
        store.insert(observation(rideId = "ride-1"))
        store.insert(observation(rideId = "ride-2"))
        store.insert(observation(rideId = "ride-1"))

        assertEquals(2, store.countForRide("ride-1"))
        assertEquals(1, store.countForRide("ride-2"))
        assertEquals(0, store.countForRide("ride-3"))
    }

    @Test
    fun onlyTheSegmentsOfTheAskedForRideComeBack() = runTest {
        store.insert(observation(rideId = "ride-1"))
        store.insert(observation(rideId = "ride-2"))
        store.insert(observation(rideId = "ride-1"))

        assertEquals(2, store.forRide("ride-1").size)
        assertEquals(1, store.forRide("ride-2").size)
        assertTrue(store.forRide("ride-3").isEmpty())
    }

    /**
     * Both boundaries and the JSON column come back out as what went in. Unresolved on purpose: what
     * is sent to a server has to be what was recorded, not what today's catalogue still asks about.
     */
    @Test
    fun aSegmentIsReadBackWholeAndStillIdKeyed() = runTest {
        store.insert(observation(rideId = "ride-1", startKind = BoundaryKind.EARLIER))

        val read = store.forRide("ride-1").single()

        assertEquals(startedAt, read.startedAt)
        assertEquals(BoundaryKind.EARLIER, read.startKind)
        assertEquals(endedAt, read.endedAt)
        assertEquals(BoundaryKind.EXACT, read.endKind)
        assertEquals(values.stored(), read.answers)
        assertEquals(setOf(CriterionValue("CARS")), read.answers["ALLOWED_USERS"])
    }

    /**
     * Nothing writes a row this class cannot read — but a row that got there anyway must not take the
     * criteria screen down with it, so the rider simply starts the next segment from nothing.
     */
    @Test
    fun anUnreadableRowLeavesTheRiderWithoutCarriedOverAnswers() = runTest {
        dao.insert(corruptRow(rideId = "ride-1"))

        assertNull(store.lastValues().first())
    }

    /** The same row on the way to the server: rather no ride than one silently short a segment. */
    @Test
    fun anUnreadableRowFailsTheReadThatASendIsBuiltFrom() = runTest {
        dao.insert(corruptRow(rideId = "ride-1"))

        assertFailsWith<SerializationException> { store.forRide("ride-1") }
    }
}

private fun corruptRow(rideId: String) = ObservationEntity(
    rideId = rideId,
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    startKind = BoundaryKind.EXACT,
    endedAtEpochMs = endedAt.toEpochMilliseconds(),
    endKind = BoundaryKind.EXACT,
    valuesJson = "{not json",
)
