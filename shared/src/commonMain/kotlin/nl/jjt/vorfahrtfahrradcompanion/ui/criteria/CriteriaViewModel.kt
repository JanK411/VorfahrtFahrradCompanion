package nl.jjt.vorfahrtfahrradcompanion.ui.criteria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Catalogue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Criterion
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.CriterionValue
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.Selections
import nl.jjt.vorfahrtfahrradcompanion.domain.criteria.StoredSelections
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.Ride
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideRecorder
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideSummary
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.segment.*
import nl.jjt.vorfahrtfahrradcompanion.service.criteria.CriteriaApi
import kotlin.time.Duration
import kotlin.time.Instant

sealed interface CriteriaUiState {
    data object Loading : CriteriaUiState
    data class Failed(val message: String) : CriteriaUiState
    data class Ready(
        val catalogue: Catalogue,
        val selections: Selections = Selections(),
        val approved: Set<Criterion> = emptySet(),
        val segment: Segment = Segment.Idle,
        val ride: Ride = Ride.Idle,
        val saveState: SaveState = SaveState.Idle,
        val pendingTiming: TimingRequest? = null,
        val pendingEnd: EndRequest? = null,
        val submitted: StoredSelections? = null,
    ) : CriteriaUiState {

        /** Approved for this segment and holding values — exactly what ending it would store. */
        val describing: List<Criterion> get() = catalogue.criteria.filter { it in approved && it.hasValues }

        /**
         * What the last segment submitted was described with, while this one still has nothing filled
         * in — a stretch much like the one before it is worth copying rather than answering again.
         * Gone the moment anything is entered, since there would be answers to overwrite.
         *
         * This is where what came out of storage becomes criteria again; one the catalogue has since
         * dropped falls away here, so it is never offered and never copied forward.
         */
        val copyable: Selections?
            get() = submitted?.let(catalogue::resolve)
                ?.takeIf { segment is Segment.Open && selections.isEmpty() && !it.isEmpty() }

        /** Carried over from the previous segment, still unapproved, so it would not be stored. */
        val carriedOver: List<Criterion> get() = catalogue.criteria.filter { it !in approved && it.hasValues }

        /** The criterion the rider still has to deal with; null once nothing is left. */
        val nextOpen: Criterion? get() = catalogue.criteria.firstOrNull { it !in approved }

        /**
         * The next criterion still needing attention *after* [criterion], in catalogue order — null past
         * the end. Moving on always moves forward: a rider who skipped one and dealt with a later one
         * meant to skip it, and does not want to be dragged back up the list for it.
         */
        fun openAfter(criterion: Criterion): Criterion? = catalogue.criteria
            .asSequence()
            .dropWhile { it != criterion }
            .drop(1)
            .firstOrNull { it !in approved }

        /**
         * What to put in front of the rider once [settled] is dealt with: the next criterion below it,
         * or — with the bottom of the list reached — whatever was skipped on the way down, which is
         * where a rider who has answered the last one still has something left to answer.
         */
        fun leadingAfter(settled: Criterion?): Criterion? =
            if (settled == null) nextOpen else openAfter(settled) ?: nextOpen

        private val Criterion.hasValues: Boolean get() = selections[this].isNotEmpty()
    }
}

/**
 * An end the rider has pressed for but not said the timing of yet. [at] is the press, so the boundary
 * keeps its moment however long they take over the answer.
 */
data class TimingRequest(val action: SegmentAction, val at: Instant)

/**
 * An end the rider has asked for but not answered for yet. [at] is when they pressed the button — the
 * boundary belongs there, not to wherever the answering ends up, give or take the step back an end
 * marked late takes.
 *
 * [asked] holds the criteria the question puts up for a decision, as they were when it was raised.
 * Editing one in the question approves it there and then, which would drop it out of a list derived from
 * the state — so the question keeps the list it opened with instead.
 */
data class EndRequest(
    val kind: BoundaryKind,
    val action: SegmentAction,
    val at: Instant,
    val asked: List<Criterion> = emptyList(),
)

sealed interface SaveState {
    data object Idle : SaveState
    data object InFlight : SaveState
    data class Error(val message: String) : SaveState
}

class CriteriaViewModel(
    private val api: CriteriaApi,
    private val observations: SegmentRecorder,
    private val rides: RideRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow<CriteriaUiState>(CriteriaUiState.Loading)
    val state: StateFlow<CriteriaUiState> = _state.asStateFlow()

    /**
     * The ride the rider is being asked to sign off, or null while none is being ended. It sits beside
     * [state] rather than inside it because it is an overlay on the screen, not a state of the screen.
     */
    private val _endingRide = MutableStateFlow<RideSummary?>(null)
    val endingRide: StateFlow<RideSummary?> = _endingRide.asStateFlow()

    private val _outcomes = MutableSharedFlow<SegmentOutcome>(extraBufferCapacity = 1)

    /** Ended segments, so the screen can report saved-versus-discarded once per end. */
    val outcomes: SharedFlow<SegmentOutcome> = _outcomes.asSharedFlow()

    private val _advances = MutableSharedFlow<Criterion>(extraBufferCapacity = 1)

    /**
     * Criteria a tap just answered — the cue to move the rider on to the next one. Emitted here rather
     * than derived in the UI because only this side knows whether a tap left a value behind or cleared it.
     */
    val advances: SharedFlow<Criterion> = _advances.asSharedFlow()

    /** Held apart from the state, so a reload of the catalogue does not lose it. */
    private val submitted =
        observations.lastSubmitted.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
        viewModelScope.launch {
            submitted.collect { values -> updateReady { copy(submitted = values) } }
        }
        viewModelScope.launch {
            observations.draft.collect { draft ->
                updateReady {
                    copy(selections = draft.selections, approved = draft.approved, segment = draft.segment)
                }
            }
        }
        viewModelScope.launch {
            rides.ride.collect { ride -> updateReady { copy(ride = ride) } }
        }
    }

    /** Reloads the catalogue; reachable from [CriteriaUiState.Failed], where no cached copy was available. */
    fun retry() = load()

    fun onTap(criterion: Criterion, value: CriterionValue) {
        observations.tap(criterion, value)
        updateReady { copy(saveState = SaveState.Idle) }

        // A tap that cleared the last value leaves nothing to move on from, so the rider stays put.
        if (observations.draft.value.selections[criterion].isNotEmpty()) _advances.tryEmit(criterion)
    }

    /** Stands by [criterion] unchanged — the rider approving a whole criterion instead of a value. */
    fun onApprove(criterion: Criterion) = observations.approve(criterion)

    /**
     * Fills the fresh segment in from the last one submitted. Nothing is approved by it: the copied
     * values land where carried-over ones do, up for review one at a time. Ignored once the rider has
     * entered anything, which is when [CriteriaUiState.Ready.copyable] stops offering it.
     */
    fun copyPrevious() {
        val values = (_state.value as? CriteriaUiState.Ready)?.copyable ?: return
        observations.preselect(values)
    }

    /** Drops the values carried over but not approved, for a stretch unlike the one before it. */
    fun clearCarriedOver() = observations.clearCarriedOver()

    /** Stands by all of them instead, for the stretch that is exactly like the one before it. */
    fun approveAll() = observations.approveAll()

    /** Takes back whichever of the two the rider just pressed. */
    fun undo() = observations.undo()

    /** Opens the ride the segments will be recorded into. */
    fun startRide() {
        viewModelScope.launch { rides.start() }
    }

    /**
     * Asks the rider to sign the open ride off, showing what it came to. The ride ends here, at the
     * press, not wherever the summary is finally saved — the same rule the segment boundary follows.
     */
    fun askToEndRide() {
        viewModelScope.launch { _endingRide.value = rides.summary(rides.now) }
    }

    /** Leaves the summary without closing the ride, which goes on running untouched. */
    fun cancelEndRide() {
        _endingRide.value = null
    }

    /** Closes the ride being signed off under [name]; see [RideRecorder.end]. */
    fun saveRide(name: String?) {
        val summary = _endingRide.value ?: return
        _endingRide.value = null
        viewModelScope.launch { rides.end(summary.endedAt, name) }
    }

    fun start(kind: BoundaryKind) = observations.start(kind)

    /**
     * The rider pressing End or Start next. How well the press hit the boundary decides whether the
     * stretch is worth keeping at all, so it is asked as a [CriteriaUiState.Ready.pendingTiming] before
     * anything else happens — see [answerTiming]. The boundary is stamped here, at the press.
     * See [SegmentRecorder.end] for what [action] does.
     */
    fun end(action: SegmentAction) {
        if (!canEnd()) return
        updateReady { copy(pendingTiming = TimingRequest(action, observations.now)) }
    }

    /** The same press, with [timing] already answered on the button itself, which skips the question. */
    fun endAs(action: SegmentAction, timing: EndTiming) {
        if (!canEnd()) return
        finish(TimingRequest(action, observations.now), timing)
    }

    /** The rider answering how well they hit the boundary, in the question the press raised. */
    fun answerTiming(timing: EndTiming) {
        val request = (_state.value as? CriteriaUiState.Ready)?.pendingTiming ?: return
        updateReady { copy(pendingTiming = null) }
        finish(request, timing)
    }

    /** Takes back the end before it was timed, leaving the segment running. */
    fun cancelTiming() = updateReady { copy(pendingTiming = null) }

    /** Nothing to end while one end is already being answered for, or being stored. */
    private fun canEnd(): Boolean {
        val ready = _state.value as? CriteriaUiState.Ready ?: return false
        // Asked of the repository rather than the state, which only catches up with it a dispatch later.
        return observations.draft.value.segment is Segment.Open &&
                ready.saveState !is SaveState.InFlight &&
                ready.pendingTiming == null &&
                ready.pendingEnd == null
    }

    /** Turns a timed press into the end it stands for: a boundary, a step back, or nothing at all. */
    private fun finish(request: TimingRequest, timing: EndTiming) {
        // Missed by longer than the grace: there is no telling where the stretch ended, so it is thrown
        // away rather than stored over ground it may well not cover.
        if (timing == EndTiming.TOO_LATE) {
            if (observations.discardSegment()) _outcomes.tryEmit(SegmentOutcome.TOO_LATE)
            return
        }

        val at = request.at - if (timing == EndTiming.SLIGHTLY_LATE) LATE_END_GRACE else Duration.ZERO
        ask(EndRequest(timing.boundary, request.action, at))
    }

    /** Stores the segment, or asks about the criteria still carried over if there are any. */
    private fun ask(request: EndRequest) {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        if (ready.carriedOver.isEmpty()) store(request)
        else updateReady { copy(pendingEnd = request.copy(asked = ready.carriedOver)) }
    }

    /**
     * Ends the segment the rider was asked about, standing by the criteria in [approve]. Everything else
     * the question put up loses its values, edits made in the question included.
     */
    fun confirmEnd(approve: Set<Criterion>) {
        val request = (_state.value as? CriteriaUiState.Ready)?.pendingEnd ?: return
        observations.resolveCarriedOver(approve, request.asked.toSet() - approve)
        updateReady { copy(pendingEnd = null) }
        store(request)
    }

    /** Takes back the end, leaving the segment running and its carried-over criteria untouched. */
    fun cancelEnd() = updateReady { copy(pendingEnd = null) }

    /**
     * The boundary a rider marks by picking [value] off a folded card: the stretch they are on ends
     * here and the next one begins, described exactly as this one was but for [criterion].
     *
     * It says two things at once, which is the whole point of it. The segment being closed is stood
     * by as it stands — picking a value off a card is saying the description held up to here — and
     * the one that opens is stood by as well, since the rider has just said what is different about
     * it. Neither asks anything: this is the one boundary that is fully answered before it is made.
     */
    fun splitSegment(criterion: Criterion, value: CriterionValue) {
        if (!canEnd()) return
        observations.approveAll()
        store(EndRequest(BoundaryKind.EXACT, SegmentAction.START_NEXT, observations.now)) {
            observations.carryOnWith(criterion, value)
        }
    }

    /** Throws the open segment away — a stretch not worth recording, or one recorded wrong. */
    fun discardSegment() {
        updateReady { copy(pendingTiming = null, pendingEnd = null) }
        if (observations.discardSegment()) _outcomes.tryEmit(SegmentOutcome.DISCARDED)
    }

    /** [andThen] runs on the segment the end opened, once the one it closed is safely stored. */
    private fun store(request: EndRequest, andThen: () -> Unit = {}) {
        viewModelScope.launch {
            updateReady { copy(saveState = SaveState.InFlight) }

            try {
                observations.end(request.kind, request.action, request.at)?.let(_outcomes::tryEmit)
                andThen()
                updateReady { copy(saveState = SaveState.Idle) }
            } catch (e: Exception) {
                fail(e.message ?: "Could not save the segment")
            }
        }
    }

    private fun load() {
        _state.value = CriteriaUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val draft = observations.draft.value
                CriteriaUiState.Ready(
                    catalogue = api.catalogue(),
                    selections = draft.selections,
                    approved = draft.approved,
                    segment = draft.segment,
                    ride = rides.ride.value,
                    submitted = submitted.value,
                )
            } catch (e: Exception) {
                CriteriaUiState.Failed(e.message ?: "Could not load the criterion catalogue")
            }
        }
    }

    private fun fail(message: String) = updateReady { copy(saveState = SaveState.Error(message)) }

    private fun updateReady(edit: CriteriaUiState.Ready.() -> CriteriaUiState.Ready) {
        _state.update { if (it is CriteriaUiState.Ready) it.edit() else it }
    }
}
