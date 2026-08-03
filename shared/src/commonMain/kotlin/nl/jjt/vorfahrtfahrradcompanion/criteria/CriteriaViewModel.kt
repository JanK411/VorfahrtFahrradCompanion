package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CriteriaUiState {
    data object Loading : CriteriaUiState
    data class Failed(val message: String) : CriteriaUiState
    data class Ready(
        val catalogue: Catalogue,
        val selections: Selections = Selections(),
        val segment: Segment = Segment.Idle,
        val ride: Ride = Ride.Idle,
        val saveState: SaveState = SaveState.Idle,
    ) : CriteriaUiState
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

    init {
        load()
        viewModelScope.launch {
            observations.draft.collect { draft ->
                updateReady { copy(selections = draft.selections, segment = draft.segment) }
            }
        }
        viewModelScope.launch {
            rides.ride.collect { ride -> updateReady { copy(ride = ride) } }
        }
    }

    /** Reloads the catalogue; reachable from [CriteriaUiState.Failed], where no cached copy was available. */
    fun retry() = load()

    fun onSelect(criterion: Criterion, value: String) {
        observations.select(criterion, value)
        updateReady { copy(saveState = SaveState.Idle) }
    }

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
                observations.end(kind, action)
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
                CriteriaUiState.Ready(api.catalogue(), draft.selections, draft.segment, rides.current())
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
