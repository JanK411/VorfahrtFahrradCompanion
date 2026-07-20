package nl.jjt.vorfahrtfahrradcompanion.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface LocationUiState {
    data object Waiting : LocationUiState
    data class Fix(val location: Location) : LocationUiState
    data class Failed(val message: String) : LocationUiState
}

class LocationViewModel(provider: LocationProvider) : ViewModel() {

    val state: StateFlow<LocationUiState> = provider.locations()
        .map<Location, LocationUiState> { LocationUiState.Fix(it) }
        .catch { emit(LocationUiState.Failed(it.message ?: "Location unavailable")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationUiState.Waiting)
}
