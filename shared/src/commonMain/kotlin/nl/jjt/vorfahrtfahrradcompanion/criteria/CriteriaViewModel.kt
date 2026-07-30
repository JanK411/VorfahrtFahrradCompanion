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
        val reviewed: Set<String> = emptySet(),
        val segment: Segment = Segment.Idle,
        val saveState: SaveState = SaveState.Idle,
    ) : CriteriaUiState {

        /** Confirmed for this segment and holding values — exactly what ending it would store. */
        val confirmed: List<Criterion> get() = catalogue.criteria.filter { it.id in reviewed && it.hasValues }

        /** Carried over from the previous segment, still unconfirmed, so it would not be stored. */
        val carriedOver: List<Criterion> get() = catalogue.criteria.filter { it.id !in reviewed && it.hasValues }

        /** The criterion the rider still has to deal with; null once nothing is left. */
        val nextOpen: Criterion? get() = catalogue.criteria.firstOrNull { it.id !in reviewed }

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
) : ViewModel() {

    private val _state = MutableStateFlow<CriteriaUiState>(CriteriaUiState.Loading)
    val state: StateFlow<CriteriaUiState> = _state.asStateFlow()

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
                    copy(selections = draft.selections, reviewed = draft.reviewed, segment = draft.segment)
                }
            }
        }
    }

    /** Reloads the catalogue; reachable from [CriteriaUiState.Failed], where no cached copy was available. */
    fun retry() = load()

    fun onTap(criterion: Criterion, value: String) {
        observations.tap(criterion, value)
        updateReady { copy(saveState = SaveState.Idle) }

        // A tap that cleared the last value leaves nothing to move on from, so the rider stays put.
        val draft = observations.draft.value
        if (draft.selections[criterion].isNotEmpty()) _advances.tryEmit(criterion)
    }

    /** Stands by [criterion] unchanged — the rider approving a whole criterion instead of a value. */
    fun onConfirm(criterion: Criterion) = observations.confirm(criterion)

    fun onKeepAll() {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        observations.keepAll(ready.catalogue.criteria)
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
                CriteriaUiState.Ready(api.catalogue(), draft.selections, draft.reviewed, draft.segment)
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
