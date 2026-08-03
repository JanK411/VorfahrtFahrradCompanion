package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CriteriaUiState {
    data object Loading : CriteriaUiState
    data class Failed(val message: String) : CriteriaUiState
    data class Ready(
        val catalogue: Catalogue,
        val selections: Selections = Selections(),
        val approved: Set<String> = emptySet(),
        val segment: Segment = Segment.Idle,
        val ride: Ride = Ride.Idle,
        val saveState: SaveState = SaveState.Idle,
    ) : CriteriaUiState {

        /** Approved for this segment and holding values — exactly what ending it would store. */
        val describing: List<Criterion> get() = catalogue.criteria.filter { it.id in approved && it.hasValues }

        /** Carried over from the previous segment, still unapproved, so it would not be stored. */
        val carriedOver: List<Criterion> get() = catalogue.criteria.filter { it.id !in approved && it.hasValues }

        /** The criterion the rider still has to deal with; null once nothing is left. */
        val nextOpen: Criterion? get() = catalogue.criteria.firstOrNull { it.id !in approved }

        /**
         * The next criterion still needing attention *after* [criterion], in catalogue order — null past
         * the end. Moving on always moves forward: a rider who skipped one and dealt with a later one
         * meant to skip it, and does not want to be dragged back up the list for it.
         */
        fun openAfter(criterion: Criterion): Criterion? = catalogue.criteria
            .asSequence()
            .dropWhile { it.id != criterion.id }
            .drop(1)
            .firstOrNull { it.id !in approved }

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

sealed interface SaveState {
    data object Idle : SaveState
    data object InFlight : SaveState
    data class Error(val message: String) : SaveState
}

class CriteriaViewModel(
    private val api: CriteriaApi,
    private val observations: ObservationRepository,
    private val rides: RideRepository,
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

    init {
        load()
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

    fun onTap(criterion: Criterion, value: String) {
        observations.tap(criterion, value)
        updateReady { copy(saveState = SaveState.Idle) }

        // A tap that cleared the last value leaves nothing to move on from, so the rider stays put.
        if (observations.draft.value.selections[criterion].isNotEmpty()) _advances.tryEmit(criterion)
    }

    /** Stands by [criterion] unchanged — the rider approving a whole criterion instead of a value. */
    fun onApprove(criterion: Criterion) = observations.approve(criterion)

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

    /** Closes the ride being signed off under [name]; see [RideRepository.end]. */
    fun saveRide(name: String?) {
        val summary = _endingRide.value ?: return
        _endingRide.value = null
        viewModelScope.launch { rides.end(summary.endedAt, name) }
    }

    fun start(kind: BoundaryKind) = observations.start(kind)

    /** Ends the open segment and stores it; see [ObservationRepository.end] for what [action] does. */
    fun end(kind: BoundaryKind, action: SegmentAction) {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        if (ready.saveState is SaveState.InFlight) return

        viewModelScope.launch {
            updateReady { copy(saveState = SaveState.InFlight) }

            try {
                observations.end(kind, action)?.let(_outcomes::tryEmit)
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
