package nl.jjt.vorfahrtfahrradcompanion.criteria

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import nl.jjt.vorfahrtfahrradcompanion.FakeClock
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
    private val last = MutableStateFlow<String?>(null)

    override suspend fun insert(entity: ObservationEntity) {
        inserted += entity
        last.value = entity.valuesJson
    }

    override suspend fun countForRide(rideId: Long) = inserted.count { it.rideId == rideId }

    override fun lastValuesJson(): Flow<String?> = last
}

private class FakeRideDao : RideDao {
    val rows = mutableListOf<RideEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: RideEntity): Long {
        val id = nextId++
        rows += entity.copy(id = id)
        return id
    }

    override suspend fun close(id: Long, endedAtEpochMs: Long, name: String?) {
        val at = rows.indexOfFirst { it.id == id }
        if (at >= 0) rows[at] = rows[at].copy(endedAtEpochMs = endedAtEpochMs, name = name)
    }

    override suspend fun delete(id: Long) {
        rows.removeAll { it.id == id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CriteriaViewModelTest {

    private val dao = FakeObservationDao()
    private val rideDao = FakeRideDao()
    private val clock = FakeClock(startedAt)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(): CriteriaViewModel {
        val rides = RideRepository(rideDao, dao, clock)
        return CriteriaViewModel(FakeApi(), ObservationRepository(dao, rides, clock), rides)
    }

    /** A view model with a ride already running — the only state in which segments can be recorded. */
    private fun TestScope.riding(): CriteriaViewModel {
        val vm = vm()
        vm.startRide()
        testScheduler.advanceUntilIdle()
        return vm
    }

    private val stored get() = dao.inserted.single()

    private val storedRide get() = rideDao.rows.single()

    /** Everything [flow] emits from here on, as a list that fills up as the test runs. */
    private fun <T> TestScope.record(flow: Flow<T>): List<T> {
        val recorded = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { recorded += it } }
        testScheduler.advanceUntilIdle()
        return recorded
    }

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
        vm.onTap(width, "W_1")
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
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

        // Something has to be approved, or the segment describes nothing and is discarded.
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EARLIER)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.SLIGHTLY_LATE)
        testScheduler.advanceUntilIdle()

        assertEquals(BoundaryKind.EARLIER, stored.startKind)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun endingWithoutAStartIsIgnored() = runTest {
        val vm = riding()

        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
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

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.START_NEXT, EndTiming.SLIGHTLY_LATE)
        testScheduler.advanceUntilIdle()

        // The end of one stretch is the start of the next — including how late it was marked, and the
        // step back that being late is worth.
        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt + stretch - LateEndGrace, BoundaryKind.EARLIER), segment)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun onlyWhatTheRiderApprovedForThisSegmentIsStored() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        // The second stretch is described by WIDTH alone; CARS is last segment's answer, never stood by.
        vm.onTap(width, "W_1")
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.confirmEnd(approve = emptySet())
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_1"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun theFirstTapOnACarriedOverValueApprovesItRatherThanClearingIt() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        vm.onTap(users, "CARS")
        testScheduler.advanceUntilIdle()

        // Tapping what is already there says "yes, this one too" — it must not toggle CARS off.
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), ready.selections[users])
        assertEquals(listOf(users), ready.describing)

        // Approved now, so a second tap is an ordinary one and does toggle.
        vm.onTap(users, "CARS")
        testScheduler.advanceUntilIdle()
        assertEquals(emptySet(), (vm.state.value as CriteriaUiState.Ready).selections[users])
    }

    @Test
    fun approvingACriterionStandsByItWithoutTouchingItsValues() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        vm.onApprove(users)
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), ready.selections[users])
        assertEquals(listOf(users), ready.describing)
        assertEquals(emptyList(), ready.carriedOver)
    }

    @Test
    fun selectionsCarryOverIntoTheSegmentThatContinuesFromTheSameBoundary() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)

        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), state.selections[users])
        assertEquals(SaveState.Idle, state.saveState)
        // Carried over, but no longer approved: the previous segment's answer is only a suggestion now.
        assertEquals(emptySet(), state.approved)
        assertEquals(listOf(users), state.carriedOver)
        assertEquals(emptyList(), state.describing)
    }

    @Test
    fun stoppingLeavesNothingBehindForTheNextSegment() = runTest {
        val vm = riding()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        // Ending the survey outright: whatever is described next starts from scratch.
        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(Segment.Idle, state.segment)
        assertEquals(emptyList(), state.carriedOver)
        assertEquals(emptySet(), state.selections[users])
    }

    @Test
    fun aSegmentNobodyApprovedAnythingInIsDiscardedRatherThanStoredEmpty() = runTest {
        val vm = riding()
        val outcomes = record(vm.outcomes)

        aSegmentThenTheNext(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.confirmEnd(approve = emptySet())
        testScheduler.advanceUntilIdle()

        // Only the first segment reached the database; the second said nothing at all.
        assertEquals(1, dao.inserted.size)
        assertEquals(listOf(SegmentOutcome.SAVED, SegmentOutcome.NOTHING_TO_STORE), outcomes)
    }

    @Test
    fun thereIsNothingToCopyUntilSomethingHasBeenSubmitted() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).copyable)
    }

    @Test
    fun aFreshSegmentCanBeFilledInFromTheLastOneSubmitted() = runTest {
        val vm = riding()

        aSubmittedSegmentThenAFreshOne(vm)

        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to setOf("CARS"))),
            (vm.state.value as CriteriaUiState.Ready).copyable,
        )

        vm.copyPrevious()
        testScheduler.advanceUntilIdle()

        // Copied in as suggestions, exactly where carried-over values land: up for review, not stored.
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), ready.selections[users])
        assertEquals(setOf("W_1"), ready.selections[width])
        assertEquals(emptySet(), ready.approved)
        assertEquals(listOf(width, users), ready.carriedOver)
        // With something filled in there is nothing fresh left to fill in, so the offer is gone.
        assertNull(ready.copyable)
    }

    @Test
    fun copyingIsNotOfferedOverAnswersTheRiderHasAlreadyGiven() = runTest {
        val vm = riding()

        aSubmittedSegmentThenAFreshOne(vm)
        vm.onTap(width, "W_2")
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).copyable)

        // And the action itself holds to that, whatever put it in front of the rider.
        vm.copyPrevious()
        testScheduler.advanceUntilIdle()
        assertEquals(emptySet(), (vm.state.value as CriteriaUiState.Ready).selections[users])
    }

    @Test
    fun whatWasCopiedIsOnlyStoredWhereTheRiderStoodByIt() = runTest {
        val vm = riding()

        aSubmittedSegmentThenAFreshOne(vm)
        vm.copyPrevious()
        testScheduler.advanceUntilIdle()
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun pressingEndAsksHowWellItWasCaughtBeforeStoringAnything() = runTest {
        val vm = riding()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        // Nothing is stored on the press alone — the segment waits on the timing.
        assertEquals(emptyList(), dao.inserted)
        assertEquals(SegmentAction.STOP, (vm.state.value as CriteriaUiState.Ready).pendingTiming?.action)

        vm.answerTiming(EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).pendingTiming)
        assertEquals(BoundaryKind.EXACT, stored.endKind)
    }

    @Test
    fun theBoundaryIsWhereTheRiderPressedEndNotWhereTheyAnsweredTheTiming() = runTest {
        val vm = riding()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        val pressedAt = clock.now()

        vm.end(SegmentAction.STOP)
        // Answering takes a while — the boundary must not travel with it.
        clock += stretch
        vm.answerTiming(EndTiming.SLIGHTLY_LATE)
        testScheduler.advanceUntilIdle()

        assertEquals((pressedAt - LateEndGrace).toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun takingBackTheTimingLeavesTheSegmentRunning() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.end(SegmentAction.STOP)
        vm.cancelTiming()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertNull(ready.pendingTiming)
        assertEquals(emptyList(), dao.inserted)
        assertEquals(Segment.Open(startedAt, BoundaryKind.EXACT), ready.segment)
    }

    @Test
    fun anEndMissedByLongerThanTheGraceThrowsTheSegmentAway() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = record(vm.outcomes)

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.TOO_LATE)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(listOf(SegmentOutcome.TOO_LATE), outcomes)
        assertEquals(Segment.Idle, (vm.state.value as CriteriaUiState.Ready).segment)
    }

    @Test
    fun aLateEndStillAsksAboutWhatIsCarriedOver() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        clock += stretch
        val pressedAt = clock.now()

        vm.endAs(SegmentAction.STOP, EndTiming.SLIGHTLY_LATE)

        // The slide answers one question; what is carried over is still the other, and the step back
        // survives it.
        assertNotNull((vm.state.value as CriteriaUiState.Ready).pendingEnd)

        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()
        assertEquals((pressedAt - LateEndGrace).toEpochMilliseconds(), dao.inserted.last().endedAtEpochMs)
    }

    @Test
    fun anEndCannotLandBeforeItsOwnStart() = runTest {
        val vm = riding()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        // Ended late within a second of starting: the step back would reach behind the start.
        vm.endAs(SegmentAction.STOP, EndTiming.SLIGHTLY_LATE)
        testScheduler.advanceUntilIdle()

        assertEquals(startedAt.toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(stored.startedAtEpochMs, stored.endedAtEpochMs)
    }

    @Test
    fun endingWithCarriedOverValuesAsksBeforeStoringAnything() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        // Only the first segment is stored; the second waits on an answer.
        assertEquals(1, dao.inserted.size)
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(listOf(users), ready.carriedOver)
        assertEquals(BoundaryKind.EXACT, ready.pendingEnd?.kind)
        assertEquals(SegmentAction.STOP, ready.pendingEnd?.action)
    }

    @Test
    fun theAnswerKeepsWhatWasApprovedAndDropsTheRest() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
        assertNull((vm.state.value as CriteriaUiState.Ready).pendingEnd)
    }

    @Test
    fun anAnswerChangedOnTheWayOutIsStoredAsChanged() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        // The question keeps the list it opened with, so editing one does not take it out of the answer.
        assertEquals(listOf(width, users), (vm.state.value as CriteriaUiState.Ready).pendingEnd?.asked)

        vm.onTap(width, "W_2")
        vm.confirmEnd(approve = setOf(width.id))
        testScheduler.advanceUntilIdle()

        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_2"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun anAnswerChangedOnTheWayOutIsStillDroppedIfItIsThenTurnedDown() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.onTap(width, "W_2")
        vm.confirmEnd(approve = emptySet())
        testScheduler.advanceUntilIdle()

        // Changing a value stands by it there and then; crossing it out afterwards takes that back.
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun theBoundaryIsWhereTheRiderPressedEndNotWhereTheyAnswered() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        clock += stretch
        val pressedAt = clock.now()

        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        // Standing at a junction thinking about it must not lengthen the stretch.
        clock += stretch
        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()

        assertEquals(pressedAt.toEpochMilliseconds(), dao.inserted.last().endedAtEpochMs)
    }

    @Test
    fun takingBackTheEndLeavesTheSegmentRunning() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        vm.cancelEnd()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertNull(ready.pendingEnd)
        assertEquals(Segment.Open(startedAt + stretch, BoundaryKind.EXACT), ready.segment)
        assertEquals(listOf(users), ready.carriedOver)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun clearingDropsWhatIsCarriedOverAndKeepsWhatIsApproved() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        vm.onTap(width, "W_2")
        vm.clearCarriedOver()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptySet(), ready.selections[users])
        assertEquals(setOf("W_2"), ready.selections[width])
        assertEquals(emptyList(), ready.carriedOver)
        // Still recording; only what described the last stretch is gone.
        assertEquals(Segment.Open(startedAt + stretch, BoundaryKind.EXACT), ready.segment)
    }

    @Test
    fun approvingAllStandsByEveryCarriedOverValueAtOnce() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        vm.approveAll()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptyList(), ready.carriedOver)
        assertEquals(listOf(width, users), ready.describing)

        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun approvingAllCanBeTakenBack() = runTest {
        val vm = riding()

        bothCarriedOverIntoTheNextSegment(vm)
        // One of them approved by hand first: taking back the whole lot must not take that with it.
        vm.onApprove(width)
        vm.approveAll()
        vm.undo()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(listOf(users), ready.carriedOver)
        assertEquals(listOf(width), ready.describing)
    }

    @Test
    fun clearingCanBeTakenBack() = runTest {
        val vm = riding()

        aSegmentThenTheNext(vm)
        vm.clearCarriedOver()
        vm.undo()
        testScheduler.advanceUntilIdle()

        // Back to carried over and waiting for a nod, exactly as before the clear.
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), ready.selections[users])
        assertEquals(listOf(users), ready.carriedOver)
    }

    @Test
    fun pickingAValueOffACardEndsTheSegmentAndCarriesOnWithThatOneChange() = runTest {
        val vm = riding()

        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        val pickedAt = clock.now()

        vm.splitSegment(width, "W_2")
        testScheduler.advanceUntilIdle()

        // The stretch just ridden is stored as it was described, ending where the value was picked.
        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(stored.valuesJson),
        )
        assertEquals(pickedAt.toEpochMilliseconds(), stored.endedAtEpochMs)

        // And the next one is already described: the same, but for the one thing that changed — and
        // stood by, so it can be ended without being asked about any of it.
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(Segment.Open(pickedAt, BoundaryKind.EXACT), ready.segment)
        assertEquals(setOf("W_2"), ready.selections[width])
        assertEquals(setOf("CARS"), ready.selections[users])
        assertEquals(emptyList(), ready.carriedOver)
        assertEquals(listOf(width, users), ready.describing)
    }

    @Test
    fun pickingAValueStandsByWhatWasStillCarriedOver() = runTest {
        val vm = riding()

        // The second segment inherits CARS unapproved, and nothing else is answered in it.
        aSegmentThenTheNext(vm)
        clock += stretch
        vm.splitSegment(width, "W_1")
        testScheduler.advanceUntilIdle()

        // Picking a value says the description held up to here, so the inherited value is stored
        // rather than dropped — and the rider is asked nothing on the way out.
        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
        assertNull((vm.state.value as CriteriaUiState.Ready).pendingEnd)
    }

    @Test
    fun pickingAValueIsIgnoredWhileNoSegmentIsRunning() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(width, "W_1")
        vm.splitSegment(width, "W_2")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(Segment.Idle, (vm.state.value as CriteriaUiState.Ready).segment)
    }

    @Test
    fun pickingOffACardSetsAPickOneAndTogglesAPickAny() {
        // Picking what is already there must not leave the next segment with nothing to describe it.
        assertEquals(setOf("W_1"), Selections(mapOf("WIDTH" to setOf("W_1"))).pick(width, "W_1")[width])
        assertEquals(setOf("W_2"), Selections(mapOf("WIDTH" to setOf("W_1"))).pick(width, "W_2")[width])

        // A criterion that holds several values is a different question: there, picking one it
        // already holds is how a rider says that one no longer applies.
        val both = Selections(mapOf("ALLOWED_USERS" to setOf("CARS", "CYCLISTS")))
        assertEquals(setOf("CYCLISTS"), both.pick(users, "CARS")[users])
        assertEquals(
            setOf("CARS", "CYCLISTS"),
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))).pick(users, "CYCLISTS")[users],
        )
    }

    @Test
    fun discardingASegmentStoresNothingAndLeavesNothingBehind() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = record(vm.outcomes)

        vm.start(BoundaryKind.EXACT)
        vm.onTap(users, "CARS")
        clock += stretch
        vm.discardSegment()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(listOf(SegmentOutcome.DISCARDED), outcomes)

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(Segment.Idle, ready.segment)
        assertEquals(emptySet(), ready.selections[users])
        assertNull(ready.pendingEnd)
    }

    @Test
    fun discardingWithNoSegmentRunningSaysNothing() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = record(vm.outcomes)

        vm.discardSegment()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), outcomes)
    }

    @Test
    fun theFlowMovesDownTheListThenComesBackForWhatWasSkipped() {
        val criteria = (1..5).map { Criterion("C$it", CriterionKind.SINGLE, listOf("A", "B")) }
        val state = CriteriaUiState.Ready(Catalogue(criteria), approved = setOf("C1", "C2", "C4"))

        // C3 was skipped and C4 dealt with: the way on is C5, not back up to C3.
        assertEquals(criteria[4], state.openAfter(criteria[3]))
        assertEquals(criteria[4], state.leadingAfter(criteria[3]))
        assertEquals(criteria[2], state.nextOpen)
        assertNull(state.openAfter(criteria[4]))

        // With the last one answered there is nothing below to go on to, so the skipped C3 comes round.
        val bottom = state.copy(approved = state.approved + "C5")
        assertEquals(criteria[2], bottom.leadingAfter(criteria[4]))

        // And once that is answered too, there is nowhere left to lead.
        assertNull(bottom.copy(approved = criteria.map(Criterion::id).toSet()).leadingAfter(criteria[2]))
    }

    @Test
    fun aTapThatClearsTheLastValueAsksForNoAdvance() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val advanced = record(vm.advances)

        vm.onTap(width, "W_1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(width), advanced)

        // Clearing a value leaves the rider with nothing answered, so the screen must stay put.
        vm.onTap(width, "W_1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(width), advanced)
    }

    /** Rides one segment with [users] = CARS and leaves the next one open, inheriting it unapproved. */
    private suspend fun TestScope.aSegmentThenTheNext(vm: CriteriaViewModel) {
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.START_NEXT, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun aSegmentIsNotStoredOutsideARide() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
    }

    @Test
    fun aStoredSegmentBelongsToTheOpenRide() = runTest {
        val vm = riding()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
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

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.START_NEXT, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()

        // The next stretch is like the last one, which the rider says by standing by CARS again —
        // without that the segment describes nothing and would be discarded rather than counted.
        vm.onTap(users, "CARS")
        testScheduler.advanceUntilIdle()

        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
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

        assertEquals(emptyList(), rideDao.rows)
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

    private fun TestScope.recordASegment(vm: CriteriaViewModel) {
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()
    }

    /**
     * Stores one segment describing both criteria, ends the survey, and opens a fresh segment — one
     * with nothing carried over, which is where copying the previous one is offered.
     */
    private suspend fun TestScope.aSubmittedSegmentThenAFreshOne(vm: CriteriaViewModel) {
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.STOP, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()
        vm.start(BoundaryKind.EXACT)
        testScheduler.advanceUntilIdle()
    }

    /** Rides one segment with both criteria filled, so both carry over into the next one. */
    private suspend fun TestScope.bothCarriedOverIntoTheNextSegment(vm: CriteriaViewModel) {
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += stretch
        vm.endAs(SegmentAction.START_NEXT, EndTiming.PRECISE)
        testScheduler.advanceUntilIdle()
    }
}
