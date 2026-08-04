package nl.jjt.vorfahrtfahrradcompanion.db.observation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionKind
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.BoundaryKind
import nl.jjt.vorfahrtfahrradcompanion.testing.FakeObservationDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val startedAt = Instant.parse("2026-07-20T12:43:37Z")
private val endedAt = Instant.parse("2026-07-20T12:46:37Z")
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI)
private val values = Selections(mapOf(users to setOf(CriterionValue("CARS"))))

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

    /**
     * What comes back is keyed by id — the table has no record of what kind of question each was —
     * so it takes a catalogue to become [Selections] again.
     */
    @Test
    fun theLastStoredSelectionsAreReadBackWhole() = runTest {
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)

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
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)
        store.insert(2, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)
        store.insert(1, startedAt, BoundaryKind.EXACT, endedAt, BoundaryKind.EXACT, values)

        assertEquals(2, store.countForRide(1))
        assertEquals(1, store.countForRide(2))
        assertEquals(0, store.countForRide(3))
    }
}
