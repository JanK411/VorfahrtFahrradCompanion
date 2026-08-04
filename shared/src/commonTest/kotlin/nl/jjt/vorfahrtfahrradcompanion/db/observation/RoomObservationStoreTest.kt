package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.BoundaryKind
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeObservationDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-07-20T12:43:37Z")
private val endedAt = Instant.parse("2026-07-20T12:46:37Z")
private val values = Selections(mapOf("ALLOWED_USERS" to setOf("CARS")))

class RoomObservationStoreTest {

    private val dao = FakeObservationDao()
    private val store = RoomObservationStore(dao)

    @Test
    fun aSegmentBecomesARowWithBothBoundariesInEpochMillis() = runTest {
        store.insert(7, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EARLIER, values)

        val row = dao.rows.single()
        assertEquals(7, row.rideId)
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
    fun theSelectionsAreStoredAsJsonKeyedByCriterionId() = runTest {
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)

        assertEquals("""{"ALLOWED_USERS":["CARS"]}""", dao.rows.single().valuesJson)
    }

    @Test
    fun theLastStoredSelectionsAreReadBackWhole() = runTest {
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)

        assertEquals(values, store.lastValues().first())
    }

    @Test
    fun withNothingStoredThereIsNoLastValue() = runTest {
        assertNull(store.lastValues().first())
    }

    @Test
    fun segmentsAreCountedPerRide() = runTest {
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)
        store.insert(2, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)

        assertEquals(2, store.countForRide(1))
        assertEquals(1, store.countForRide(2))
        assertEquals(0, store.countForRide(3))
    }
}
