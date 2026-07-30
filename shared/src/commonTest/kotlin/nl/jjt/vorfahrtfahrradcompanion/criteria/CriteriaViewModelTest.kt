package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationDao
import nl.jjt.vorfahrtfahrradcompanion.criteria.db.ObservationEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val width = Criterion("WIDTH", CriterionKind.SINGLE, listOf("W_1", "W_2"))
private val users = Criterion("ALLOWED_USERS", CriterionKind.MULTI, listOf("CARS", "CYCLISTS"))
private val catalogue = Catalogue(listOf(width, users))

private val startedAt = Instant.parse("2026-07-20T12:43:37Z")
private val ride = 3.minutes

private class FakeApi : CriteriaApi {
    override suspend fun catalogue() = catalogue
}

private class FakeObservationDao : ObservationDao {
    val inserted = mutableListOf<ObservationEntity>()
    override suspend fun insert(entity: ObservationEntity) {
        inserted += entity
    }
}

class CriteriaViewModelTest {

    private val dao = FakeObservationDao()
    private val clock = FakeClock(startedAt)

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CriteriaViewModel(FakeApi(), ObservationRepository(dao, clock))

    private val stored get() = dao.inserted.single()

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
        val vm = vm()
        testScheduler.advanceUntilIdle()

        // WIDTH ends up selected-then-cleared, so it must not reach the stored values at all.
        vm.onTap(width, "W_1")
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")

        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(startedAt.toEpochMilliseconds(), stored.startedAtEpochMs)
        assertEquals(BoundaryKind.EXACT, stored.startKind)
        assertEquals((startedAt + ride).toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(BoundaryKind.EXACT, stored.endKind)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(stored.valuesJson),
        )
    }

    @Test
    fun aMissedBoundaryIsStoredAsSuch() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        // Something has to be confirmed, or the segment describes nothing and is discarded.
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EARLIER)
        clock += ride
        vm.end(BoundaryKind.EARLIER, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(BoundaryKind.EARLIER, stored.startKind)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun endingWithoutAStartIsIgnored() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(Segment.Idle, (vm.state.value as CriteriaUiState.Ready).segment)
    }

    @Test
    fun startingTwiceKeepsTheFirstBoundary() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.start(BoundaryKind.EARLIER)
        testScheduler.advanceUntilIdle()

        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt, BoundaryKind.EXACT), segment)
    }

    @Test
    fun theChainedSegmentContinuesFromTheSameBoundary() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.end(BoundaryKind.EARLIER, action = SegmentAction.START_NEXT)
        testScheduler.advanceUntilIdle()

        // The end of one stretch is the start of the next — including how late it was marked.
        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt + ride, BoundaryKind.EARLIER), segment)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun selectionsCarryOverIntoTheNextSegment() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), state.selections[users])
        assertEquals(SaveState.Idle, state.saveState)
        // Carried over, but no longer confirmed: the previous segment's answer is only a suggestion now.
        assertEquals(emptySet(), state.reviewed)
        assertEquals(listOf(users), state.carriedOver)
    }

    /** Rides one segment with [users] = CARS and leaves the next one open. */
    private suspend fun TestScope.aSegmentThenTheNext(vm: CriteriaViewModel) {
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.end(BoundaryKind.EXACT, SegmentAction.START_NEXT)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun anUnconfirmedCarryOverIsNotStoredAndItsSegmentIsDiscarded() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = mutableListOf<SegmentOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.outcomes.collect { outcomes += it } }
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        // Only the first segment was stored; the second described nothing, so it never reached the dao.
        assertEquals(1, dao.inserted.size)
        assertEquals(listOf(SegmentOutcome.SAVED, SegmentOutcome.DISCARDED), outcomes)
    }

    @Test
    fun keepingAllStoresTheCarriedValuesAgain() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        vm.onKeepAll()
        clock += ride
        vm.end(BoundaryKind.EXACT, SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun theFirstTapOnACarriedValueConfirmsItInsteadOfClearingIt() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        vm.onTap(users, "CARS")
        testScheduler.advanceUntilIdle()

        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), state.selections[users])
        assertEquals(listOf(users), state.confirmed)

        // And a second tap toggles it off, as it would on any confirmed criterion.
        vm.onTap(users, "CARS")
        testScheduler.advanceUntilIdle()
        assertEquals(emptySet(), (vm.state.value as CriteriaUiState.Ready).selections[users])
    }

    @Test
    fun theFlowWalksTheCatalogueAndRunsOut() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val ready = { vm.state.value as CriteriaUiState.Ready }

        assertEquals(width, ready().nextOpen)

        vm.onTap(width, "W_1")
        testScheduler.advanceUntilIdle()
        assertEquals(users, ready().nextOpen)

        vm.onKeepAll()
        testScheduler.advanceUntilIdle()
        assertNull(ready().nextOpen)
    }

    @Test
    fun aTapThatClearsTheLastValueAsksForNoAdvance() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val advanced = mutableListOf<Criterion>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.advances.collect { advanced += it } }
        testScheduler.advanceUntilIdle()

        vm.onTap(width, "W_1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(width), advanced)

        // Clearing a value leaves the rider with nothing answered, so the screen must stay put.
        vm.onTap(width, "W_1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(width), advanced)
    }
}
