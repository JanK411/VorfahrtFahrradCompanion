package nl.jjt.vorfahrtfahrradcompanion.ui.rides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore

/**
 * Everything the rider has recorded. A data class rather than a sealed hierarchy: an empty list is not
 * a different kind of state, it is a list with nothing in it, and what comes next — a dialog, a send in
 * flight — sits alongside the list rather than instead of it.
 */
data class RidesUiState(val rides: List<RecordedRide> = emptyList())

class RidesViewModel(rides: RideStore) : ViewModel() {

    val state: StateFlow<RidesUiState> = rides.recorded()
        .map(::RidesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RidesUiState())
}
