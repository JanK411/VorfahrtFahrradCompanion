package nl.jjt.vorfahrtfahrradcompanion.ui.rides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RecordedRide
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideState
import nl.jjt.vorfahrtfahrradcompanion.domain.recording.ride.RideStore
import nl.jjt.vorfahrtfahrradcompanion.service.ride.RideUploader

/**
 * What tapping a ride raises. A ride is sent from the row itself, so the tap has to mean three different
 * things depending on where the ride stands — and each of them is a question rather than an action, so
 * nothing leaves the device on a stray thumb.
 */
sealed interface RidePrompt {
    val ride: RecordedRide

    /**
     * Nothing to send while it is still being ridden. Dismiss is the only way out.
     *
     * TODO(VF-23): a ride the app was killed during stays open forever and this is where the rider
     *  would close it — the same ticket as the never-resumed ride in RideRecorder.
     */
    data class StillOpen(override val ride: RecordedRide) : RidePrompt

    data class Send(override val ride: RecordedRide) : RidePrompt

    /** Sending one the server already has is allowed, but never by accident. */
    data class SendAgain(override val ride: RecordedRide) : RidePrompt
}

/**
 * A data class rather than a sealed hierarchy: the list, an open question, a send in flight and a
 * message are all true at once, not instead of each other.
 *
 * [sending] holds ride ids rather than a single flag so a row knows whether *it* is the one going.
 */
data class RidesUiState(
    val rides: List<RecordedRide> = emptyList(),
    val prompt: RidePrompt? = null,
    val sending: Set<String> = emptySet(),
    val message: String? = null,
)

class RidesViewModel(
    private val rides: RideStore,
    private val uploader: RideUploader,
) : ViewModel() {

    private data class Local(
        val prompt: RidePrompt? = null,
        val sending: Set<String> = emptySet(),
        val message: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<RidesUiState> = combine(rides.recorded(), local) { recorded, own ->
        RidesUiState(recorded, own.prompt, own.sending, own.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RidesUiState())

    /** Asks before doing anything — which question depends on where the ride stands. */
    fun rideTapped(ride: RecordedRide) {
        if (ride.id in local.value.sending) return
        local.update {
            it.copy(
                prompt = when (ride.state) {
                    RideState.OPEN -> RidePrompt.StillOpen(ride)
                    RideState.FINISHED -> RidePrompt.Send(ride)
                    RideState.UPLOADED -> RidePrompt.SendAgain(ride)
                },
            )
        }
    }

    fun promptDismissed() = local.update { it.copy(prompt = null) }

    /**
     * Takes the answered question up. An open ride has nothing to confirm, so confirming one does
     * nothing beyond closing the question.
     */
    fun promptConfirmed() {
        val prompt = local.value.prompt
        local.update { it.copy(prompt = null) }
        if (prompt == null || prompt is RidePrompt.StillOpen) return
        send(prompt.ride)
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun send(ride: RecordedRide) {
        local.update { it.copy(sending = it.sending + ride.id) }
        viewModelScope.launch {
            val outcome = uploader.upload(ride)
            local.update {
                it.copy(
                    sending = it.sending - ride.id,
                    message = outcome.fold(
                        onSuccess = { "Ride sent" },
                        onFailure = { e -> "Could not send: ${e.message ?: "unknown error"}" },
                    ),
                )
            }
        }
    }
}
