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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    private val last = MutableStateFlow<String?>(null)

    override suspend fun insert(entity: ObservationEntity) {
        inserted += entity
        last.value = entity.valuesJson
    }

    override fun lastValuesJson(): Flow<String?> = last
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
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
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
        vm.endAs(SegmentAction.STOP, EndTiming.JUST_NOW)
        testScheduler.advanceUntilIdle()

        assertEquals(BoundaryKind.EARLIER, stored.startKind)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun endingWithoutAStartIsIgnored() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
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
        vm.endAs(SegmentAction.START_NEXT, EndTiming.JUST_NOW)
        testScheduler.advanceUntilIdle()

        // The end of one stretch is the start of the next — including how late it was marked, and the
        // step back that being late is worth.
        val segment = (vm.state.value as CriteriaUiState.Ready).segment
        assertEquals(Segment.Open(startedAt + ride - LateEndGrace, BoundaryKind.EARLIER), segment)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun selectionsCarryOverIntoTheSegmentThatContinuesFromTheSameBoundary() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)

        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(setOf("CARS"), state.selections[users])
        assertEquals(SaveState.Idle, state.saveState)
        // Carried over, but no longer confirmed: the previous segment's answer is only a suggestion now.
        assertEquals(emptySet(), state.reviewed)
        assertEquals(listOf(users), state.carriedOver)
    }

    @Test
    fun stoppingLeavesNothingBehindForTheNextSegment() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        testScheduler.advanceUntilIdle()

        // Ending the survey outright: whatever is described next starts from scratch.
        val state = vm.state.value as CriteriaUiState.Ready
        assertEquals(Segment.Idle, state.segment)
        assertEquals(emptyList(), state.carriedOver)
        assertEquals(emptySet(), state.selections[users])
        assertEquals(width, state.nextOpen)
    }

    /** Rides one segment with [users] = CARS and leaves the next one open. */
    private suspend fun TestScope.aSegmentThenTheNext(vm: CriteriaViewModel) {
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.endAs(SegmentAction.START_NEXT, EndTiming.EXACT)
        testScheduler.advanceUntilIdle()
    }

    /** Rides one segment with both criteria filled, so both carry over into the next one. */
    private suspend fun TestScope.bothCarriedOverIntoTheNextSegment(vm: CriteriaViewModel) {
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.endAs(SegmentAction.START_NEXT, EndTiming.EXACT)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun thereIsNothingToCopyUntilSomethingHasBeenSubmitted() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).copyable)
    }

    /**
     * Stores one segment describing both criteria, ends the survey, and opens a fresh segment — one
     * with nothing carried over, which is where copying the previous one is offered.
     */
    private suspend fun TestScope.aSubmittedSegmentThenAFreshOne(vm: CriteriaViewModel) {
        vm.onTap(width, "W_1")
        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        testScheduler.advanceUntilIdle()
        vm.start(BoundaryKind.EXACT)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun aFreshSegmentCanBeFilledInFromTheLastOneSubmitted() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

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
        assertEquals(emptySet(), ready.reviewed)
        assertEquals(listOf(width, users), ready.carriedOver)
        // With something filled in there is nothing fresh left to fill in, so the offer is gone.
        assertNull(ready.copyable)
    }

    @Test
    fun copyingIsNotOfferedOverAnswersTheRiderHasAlreadyGiven() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSubmittedSegmentThenAFreshOne(vm)
        vm.onTap(width, "W_2")
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertNull(ready.copyable)

        // And the action itself holds to that, whatever put it in front of the rider.
        vm.copyPrevious()
        testScheduler.advanceUntilIdle()
        assertEquals(emptySet(), (vm.state.value as CriteriaUiState.Ready).selections[users])
    }

    @Test
    fun whatWasCopiedIsOnlyStoredWhereTheRiderStoodByIt() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSubmittedSegmentThenAFreshOne(vm)
        vm.copyPrevious()
        testScheduler.advanceUntilIdle()
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.inserted.size)
        assertEquals(
            Selections(mapOf("ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun endingWithCarriedOverValuesAsksBeforeStoringAnything() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
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
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
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
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
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
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        vm.onTap(width, "W_2")
        vm.confirmEnd(approve = emptySet())
        testScheduler.advanceUntilIdle()

        // Changing a value stands by it there and then; crossing it out afterwards takes that back.
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun approvingNothingOnTheWayOutDiscardsTheSegment() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = mutableListOf<SegmentOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.outcomes.collect { outcomes += it } }
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        vm.confirmEnd(approve = emptySet())
        testScheduler.advanceUntilIdle()

        assertEquals(1, dao.inserted.size)
        assertEquals(listOf(SegmentOutcome.SAVED, SegmentOutcome.NOTHING_TO_STORE), outcomes)
    }

    @Test
    fun theBoundaryIsWhereTheRiderPressedEndNotWhereTheyAnswered() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        val pressedAt = clock.now()

        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        clock += ride
        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()

        assertEquals(pressedAt.toEpochMilliseconds(), dao.inserted.last().endedAtEpochMs)
    }

    @Test
    fun pressingEndAsksHowWellItWasCaughtBeforeStoringAnything() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.end(SegmentAction.STOP)
        testScheduler.advanceUntilIdle()

        // Nothing is stored on the press alone — the segment waits on the timing.
        assertEquals(emptyList(), dao.inserted)
        assertEquals(SegmentAction.STOP, (vm.state.value as CriteriaUiState.Ready).pendingTiming?.action)

        vm.answerTiming(EndTiming.EXACT)
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).pendingTiming)
        assertEquals(BoundaryKind.EXACT, stored.endKind)
    }

    @Test
    fun theBoundaryIsWhereTheRiderPressedEndNotWhereTheyAnsweredTheTiming() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        val pressedAt = clock.now()

        vm.end(SegmentAction.STOP)
        // Answering takes a while — the boundary must not travel with it.
        clock += ride
        vm.answerTiming(EndTiming.JUST_NOW)
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
        clock += ride
        vm.end(SegmentAction.STOP)
        vm.cancelTiming()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertNull(ready.pendingTiming)
        assertEquals(emptyList(), dao.inserted)
        assertEquals(Segment.Open(startedAt, BoundaryKind.EXACT), ready.segment)
    }

    @Test
    fun anEndMarkedLateIsStoredAStepBack() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        val pressedAt = clock.now()

        vm.endAs(SegmentAction.STOP, EndTiming.JUST_NOW)
        testScheduler.advanceUntilIdle()

        assertEquals((pressedAt - LateEndGrace).toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(BoundaryKind.EARLIER, stored.endKind)
    }

    @Test
    fun anEndMissedByLongerThanThatThrowsTheSegmentAway() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = mutableListOf<SegmentOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.outcomes.collect { outcomes += it } }
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.LONGER)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), dao.inserted)
        assertEquals(listOf(SegmentOutcome.TOO_LATE), outcomes)
        assertEquals(Segment.Idle, (vm.state.value as CriteriaUiState.Ready).segment)
    }

    @Test
    fun aLateEndStillAsksAboutWhatIsUnapproved() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        val pressedAt = clock.now()

        vm.endAs(SegmentAction.STOP, EndTiming.JUST_NOW)

        // The slide answers one question; what is unapproved is still the other, and the step back
        // survives it.
        assertNotNull((vm.state.value as CriteriaUiState.Ready).pendingEnd)

        vm.confirmEnd(approve = setOf(users.id))
        testScheduler.advanceUntilIdle()
        assertEquals((pressedAt - LateEndGrace).toEpochMilliseconds(), dao.inserted.last().endedAtEpochMs)
    }

    @Test
    fun anEndCannotLandBeforeItsOwnStart() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        vm.onTap(users, "CARS")
        vm.start(BoundaryKind.EXACT)
        // Ended late within a second of starting: the step back would reach behind the start.
        vm.endAs(SegmentAction.STOP, EndTiming.JUST_NOW)
        testScheduler.advanceUntilIdle()

        assertEquals(startedAt.toEpochMilliseconds(), stored.endedAtEpochMs)
        assertEquals(stored.startedAtEpochMs, stored.endedAtEpochMs)
    }

    @Test
    fun clearingDropsWhatIsPreselectedAndKeepsWhatIsApproved() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        vm.onTap(width, "W_2")
        vm.clearCarriedOver()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptySet(), ready.selections[users])
        assertEquals(setOf("W_2"), ready.selections[width])
        assertEquals(emptyList(), ready.carriedOver)
        // Still recording; only what described the last stretch is gone.
        assertEquals(Segment.Open(startedAt + ride, BoundaryKind.EXACT), ready.segment)
    }

    @Test
    fun approvingAllStandsByEveryCarriedOverValueAtOnce() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        vm.approveCarriedOver()
        testScheduler.advanceUntilIdle()

        // Nothing left to review, so ending the segment asks nothing and stores the lot.
        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(emptyList(), ready.carriedOver)
        assertEquals(listOf(width, users), ready.confirmed)

        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        testScheduler.advanceUntilIdle()

        assertNull((vm.state.value as CriteriaUiState.Ready).pendingEnd)
        assertEquals(
            Selections(mapOf("WIDTH" to setOf("W_1"), "ALLOWED_USERS" to setOf("CARS"))),
            Json.decodeFromString<Selections>(dao.inserted.last().valuesJson),
        )
    }

    @Test
    fun approvingAllCanBeTakenBack() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        bothCarriedOverIntoTheNextSegment(vm)
        // One of them approved by hand first: taking back the whole lot must not take that with it.
        vm.onConfirm(width)
        vm.approveCarriedOver()
        vm.undo()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertEquals(listOf(users), ready.carriedOver)
        assertEquals(listOf(width), ready.confirmed)
    }

    @Test
    fun clearingCanBeTakenBack() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

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
    fun discardingASegmentStoresNothingAndLeavesNothingBehind() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()
        val outcomes = mutableListOf<SegmentOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.outcomes.collect { outcomes += it } }
        testScheduler.advanceUntilIdle()

        vm.start(BoundaryKind.EXACT)
        vm.onTap(users, "CARS")
        clock += ride
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
        val outcomes = mutableListOf<SegmentOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.outcomes.collect { outcomes += it } }
        testScheduler.advanceUntilIdle()

        vm.discardSegment()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), outcomes)
    }

    @Test
    fun takingBackTheEndLeavesTheSegmentRunning() = runTest {
        val vm = vm()
        testScheduler.advanceUntilIdle()

        aSegmentThenTheNext(vm)
        clock += ride
        vm.endAs(SegmentAction.STOP, EndTiming.EXACT)
        vm.cancelEnd()
        testScheduler.advanceUntilIdle()

        val ready = vm.state.value as CriteriaUiState.Ready
        assertNull(ready.pendingEnd)
        assertEquals(Segment.Open(startedAt + ride, BoundaryKind.EXACT), ready.segment)
        assertEquals(listOf(users), ready.carriedOver)
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun theFlowMovesDownTheListThenComesBackForWhatWasSkipped() {
        val criteria = (1..5).map { Criterion("C$it", CriterionKind.SINGLE, listOf("A", "B")) }
        val state = CriteriaUiState.Ready(Catalogue(criteria), reviewed = setOf("C1", "C2", "C4"))

        // C3 was skipped and C4 dealt with: the way on is C5, not back up to C3.
        assertEquals(criteria[4], state.openAfter(criteria[3]))
        assertEquals(criteria[4], state.leadingAfter(criteria[3]))
        assertEquals(criteria[2], state.nextOpen)
        assertNull(state.openAfter(criteria[4]))

        // With the last one answered there is nothing below to go on to, so the skipped C3 comes round.
        val bottom = state.copy(reviewed = state.reviewed + "C5")
        assertEquals(criteria[2], bottom.leadingAfter(criteria[4]))

        // And once that is answered too, there is nowhere left to lead.
        assertNull(bottom.copy(reviewed = criteria.map(Criterion::id).toSet()).leadingAfter(criteria[2]))
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
