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
        val submitState: SubmitState = SubmitState.Idle,
    ) : CriteriaUiState
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data object InFlight : SubmitState
    data class Error(val message: String) : SubmitState
}

class CriteriaViewModel(
    private val api: CriteriaApi,
    private val observations: ObservationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CriteriaUiState>(CriteriaUiState.Loading)
    val state: StateFlow<CriteriaUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the catalogue; reachable from [CriteriaUiState.Failed], where no cached copy was available. */
    fun retry() = load()

    fun onSelect(criterion: Criterion, value: String) = updateReady {
        copy(selections = selections.select(criterion, value), submitState = SubmitState.Idle)
    }

    fun submit() {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        if (ready.submitState is SubmitState.InFlight) return

        viewModelScope.launch {
            updateReady { copy(submitState = SubmitState.InFlight) }

            try {
                observations.record(ready.selections)
                updateReady { copy(selections = Selections(), submitState = SubmitState.Idle) }
            } catch (e: Exception) {
                fail(e.message ?: "Could not save the observation")
            }
        }
    }

    private fun load() {
        _state.value = CriteriaUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                CriteriaUiState.Ready(api.catalogue())
            } catch (e: Exception) {
                CriteriaUiState.Failed(e.message ?: "Could not load the criterion catalogue")
            }
        }
    }

    private fun fail(message: String) = updateReady { copy(submitState = SubmitState.Error(message)) }

    private fun updateReady(edit: CriteriaUiState.Ready.() -> CriteriaUiState.Ready) {
        _state.update { if (it is CriteriaUiState.Ready) it.edit() else it }
    }
}
