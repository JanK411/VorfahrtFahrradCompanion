package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.RideEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))
private val catalogue = Catalogue(listOf(width, users))

private val startedAt = Instant.parse("2026-07-20T12:43:37Z")
private val stretch = 3.minutes

private class FakeApi : CriteriaApi {
    override suspend fun catalogue() = catalogue
}

private class FakeObservationDao : ObservationDao {
    val inserted = mutableListOf<ObservationEntity>()

    override suspend fun insert(entity: ObservationEntity) {
        inserted += entity
    }

    override suspend fun countForRide(rideId: Long) = inserted.count { it.rideId == rideId }
}

private class FakeRideDao : RideDao {
    val rows = MutableStateFlow(emptyList<RideEntity>())
    private var nextId = 1L

    override suspend fun insert(entity: RideEntity): Long {
        val id = nextId++
        rows.value += entity.copy(id = id)
        return id
    }

    override fun observeOpen(): Flow<RideEntity?> = rows.map { list -> list.lastOrNull { it.endedAtEpochMs == null } }

    override suspend fun open(): RideEntity? = rows.value.lastOrNull { it.endedAtEpochMs == null }

    override suspend fun close(id: Long, endedAtEpochMs: Long, name: String?) {
        rows.value = rows.value.map { if (it.id == id) it.copy(endedAtEpochMs = endedAtEpochMs, name = name) else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class CriteriaViewModelTest {

    private val dao = FakeObservationDao()
    private val rideDao = FakeRideDao()
    private val clock = FakeClock(startedAt)

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CriteriaViewModel(
        FakeApi(),
        ObservationRepository(dao, rideDao, clock),
        RideRepository(rideDao, dao, clock),
    )

    /** A view model with a ride already running — the only state in which segments can be recorded. */
    private fun TestScope.riding(): CriteriaViewModel {
        val vm = vm()
        vm.startRide()
        testScheduler.advanceUntilIdle()
        return vm
    }

    private val stored get() = dao.inserted.single()

    private val storedRide get() = rideDao.rows.value.single()

    @Test
    fun singleSelectionReplacesAndClears() {
        var selections = Selections()

        selections = selections.select(width, "W_1")
        assertEquals(setOf("W_1"), selections[width])

        // A different chip replaces
        selections = selections.select(width, "W_2")
        assertEquals(setOf("W_2"), selections[width])

        // The selected chip clears
        selections = selections.select(width, "W_2")
        assertEquals(emptySet(), selections[width])
    }

    @Test
    fun multiSelectionToggles() {
        var selections = Selections()

        selections = selections.select(users, "CARS")
        selections = selections.select(users, "CYCLISTS")
        assertEquals(setOf("CARS", "CYCLISTS"), selections[users])

        selections = selections.select(users, "CARS")
        assertEquals(setOf("CYCLISTS"), selections[users])
    }

    @Test
    fun aSegmentIsStoredWithBothBoundaries() = runTest {
        val vm = riding()

        // WIDTH ends up selected-then-cleared, so it must not reach the stored values at all.
        vm.onSelect(width, "W_1")
        vm.onSelect(width, "W_1")
        vm.onSelect(users, "CARS")

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(startedAt.toEpochMilliseconds(), stored.startedAtEpochMs)
        assertEquals(BoundaryKind.EXACT, stored.startKind)
        assertEquals((startedAt + stretch).toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(BoundaryKind.EXACT, stored.endKind)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(stored.valuesJson),
        )
    }

    @Test
    fun aMissedBoundaryIsStoredAsSuch() = runTest {
        val vm = riding()

        vm.start(BoundaryKind.EARLIER)
        clock += stretch
        vm.end(BoundaryKind.EARLIER, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(BoundaryKind.EARLIER, stored.startKind)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun endingWithoutAStartIsIgnored() = runTest {
        val vm = riding()

        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(Segment.Idle, (vm.state.value as CriteriaUiState.Ready).segment)
    }

    @Test
    fun startingTwiceKeepsTheFirstBoundary() = runTest {
        val vm = riding()

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.start(BoundaryKind.EARLIER)
        testScheduler.advanceUntilIdle()

        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt, BoundaryKind.EXACT), segment)
    }

    @Test
    fun theChainedSegmentContinuesFromTheSameBoundary() = runTest {
        val vm = riding()

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EARLIER, action = SegmentAction.START_NEXT)
        testScheduler.advanceUntilIdle()

        // The end of one stretch is the start of the next — including how late it was marked.
        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt + stretch, BoundaryKind.EARLIER), segment)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun selectionsCarryOverIntoTheNextSegment() = runTest {
        val vm = riding()

        vm.onSelect(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), state.selections[users])
        assertEquals(SaveState.Idle, state.saveState)
    }

    @Test
    fun aSegmentIsNotStoredOutsideARide() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
    }

    @Test
    fun aStoredSegmentBelongsToTheOpenRide() = runTest {
        val vm = riding()

        vm.onSelect(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(storedRide.id, stored.rideId)
        assertEquals(startedAt.toEpochMilliseconds(), storedRide.startedAtEpochMs)
        assertNull(storedRide.endedAtEpochMs)
    }

    @Test
    fun startingARideTwiceKeepsTheFirstOne() = runTest {
        val vm = riding()

        clock += stretch
        vm.startRide()
        testScheduler.advanceUntilIdle()

        assertEquals(startedAt.toEpochMilliseconds(), storedRide.startedAtEpochMs)
    }

    @Test
    fun theSummaryReportsWhatTheRideCameTo() = runTest {
        val vm = riding()

        vm.onSelect(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.START_NEXT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        vm.askToEndRide()
        testScheduler.advanceUntilIdle()

        val summary = assertNotNull(vm.endingRide.value)
        assertEquals(startedAt, summary.startedAt)
        assertEquals(startedAt + stretch + stretch, summary.endedAt)
        assertEquals(2, summary.segments)
    }

    @Test
    fun aRideEndsWhereItWasAskedFor_notWhereItWasSaved() = runTest {
        val vm = riding()
        recordASegment(vm)

        vm.askToEndRide()
        testScheduler.advanceUntilIdle()

        // The rider takes their time over the name; the ride still ended when they pressed the button.
        clock += stretch
        vm.saveRide("Kanaaldijk")
        testScheduler.advanceUntilIdle()

        assertEquals((startedAt + stretch).toEpochMilliseconds(), storedRide.endedAtEpochMs)
        assertEquals("Kanaaldijk", storedRide.name)
        assertNull(vm.endingRide.value)
    }

    @Test
    fun aRideSavedWithoutANameKeepsNone() = runTest {
        val vm = riding()
        recordASegment(vm)

        vm.askToEndRide()
        testScheduler.advanceUntilIdle()
        vm.saveRide("   ")
        testScheduler.advanceUntilIdle()

        assertNull(storedRide.name)
    }

    @Test
    fun aRideWithoutSegmentsIsThrownAwayRatherThanStored() = runTest {
        val vm = riding()

        vm.askToEndRide()
        testScheduler.advanceUntilIdle()
        vm.saveRide(null)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), rideDao.rows.value)
        assertEquals(Ride.Idle, (vm.state.value as CriteriaUiState.Ready).ride)
    }

    @Test
    fun leavingTheSummaryLetsTheRideRunOn() = runTest {
        val vm = riding()
        recordASegment(vm)

        vm.askToEndRide()
        testScheduler.advanceUntilIdle()
        vm.cancelEndRide()
        testScheduler.advanceUntilIdle()

        assertNull(vm.endingRide.value)
        assertNull(storedRide.endedAtEpochMs)
        assertTrue((vm.state.value as CriteriaUiState.Ready).ride is Ride.Open)
    }

    @Test
    fun aRideLeftOpenIsStillThereOnTheNextStart() = runTest {
        riding()
        clock += stretch

        // A second view model stands in for the app being started again over the same database.
        val next = vm()
        testScheduler.advanceUntilIdle()

        val ride = (next.state.value as CriteriaUiState.Ready).ride
        assertEquals(Ride.Open(storedRide.id, startedAt), ride)
    }

    private fun TestScope.recordASegment(vm: CriteriaViewModel) {
        vm.onSelect(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()
    }
}
