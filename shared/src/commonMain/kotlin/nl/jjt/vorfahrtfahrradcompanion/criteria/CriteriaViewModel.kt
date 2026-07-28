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
) : ViewModel() {

    private val _state = MutableStateFlow<CriteriaUiState>(CriteriaUiState.Loading)
    val state: StateFlow<CriteriaUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            observations.draft.collect { draft ->
                updateReady { copy(selections = draft.selections, segment = draft.segment) }
            }
        }
    }

    /** Reloads the catalogue; reachable from [CriteriaUiState.Failed], where no cached copy was available. */
    fun retry() = load()

    fun onSelect(criterion: Criterion, value: String) {
        observations.select(criterion, value)
        updateReady { copy(saveState = SaveState.Idle) }
    }

    fun start(kind: BoundaryKind) = observations.start(kind)

    /** Ends the open segment and stores it; see [ObservationRepository.end] for what [startNext] does. */
    fun end(kind: BoundaryKind, startNext: Boolean = false) {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        if (ready.saveState is SaveState.InFlight) return

        viewModelScope.launch {
            updateReady { copy(saveState = SaveState.InFlight) }

            try {
                observations.end(kind, startNext)
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
                CriteriaUiState.Ready(api.catalogue(), draft.selections, draft.segment)
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
