package nl.jjt.vorfahrtfahrradcompanion.criteria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import nl.jjt.vorfahrtfahrradcompanion.location.LocationProvider
import kotlin.time.Duration.Companion.seconds

sealed interface CriteriaUiState {
    data object Loading : CriteriaUiState
    data class Failed(val message: String) : CriteriaUiState
    data class Ready(
        val catalogue: Catalogue,
        val selections: Map<String, Set<String>> = emptyMap(),
        val submitState: SubmitState = SubmitState.Idle,
    ) : CriteriaUiState
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data object InFlight : SubmitState
    data class Error(val message: String) : SubmitState
}

private val FIX_TIMEOUT = 15.seconds

/**
 * Applies a chip tap. [CriterionKind] is the only thing that differs between criteria, which is what
 * lets one screen render a catalogue it has never seen.
 */
internal fun Map<String, Set<String>>.select(
    criterion: Criterion,
    value: String,
): Map<String, Set<String>> {
    val current = this[criterion.id].orEmpty()
    val next = when (criterion.kind) {
        CriterionKind.SINGLE -> if (value in current) emptySet() else setOf(value)
        CriterionKind.MULTI -> if (value in current) current - value else current + value
    }
    return this + (criterion.id to next)
}

class CriteriaViewModel(
    private val api: CriteriaApi,
    private val locations: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<CriteriaUiState>(CriteriaUiState.Loading)
    val state: StateFlow<CriteriaUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** No cache: the catalogue is authoritative server-side, so it is fetched on every screen open. */
    fun retry() = load()

    fun onSelect(criterion: Criterion, value: String) = updateReady {
        copy(selections = selections.select(criterion, value), submitState = SubmitState.Idle)
    }

    fun submit() {
        val ready = _state.value as? CriteriaUiState.Ready ?: return
        if (ready.submitState is SubmitState.InFlight) return

        viewModelScope.launch {
            updateReady { copy(submitState = SubmitState.InFlight) }

            val location = try {
                withTimeout(FIX_TIMEOUT) { locations.locations().first() }
            } catch (_: TimeoutCancellationException) {
                return@launch fail("No GPS fix within ${FIX_TIMEOUT.inWholeSeconds} s")
            } catch (e: Exception) {
                return@launch fail(e.message ?: "Location unavailable — is the permission granted?")
            }

            try {
                // Unset criteria are simply omitted; there is no validation.
                api.submit(Observation(location, ready.selections.filterValues { it.isNotEmpty() }))
                updateReady { copy(selections = emptyMap(), submitState = SubmitState.Idle) }
            } catch (e: Exception) {
                fail(e.message ?: "Submit failed")
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
