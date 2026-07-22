package nl.jjt.vorfahrtfahrradcompanion.patchnotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatchNotesUiState(
    val loading: Boolean = true,
    val newNotes: List<PatchNote> = emptyList(),
    val olderNotes: List<PatchNote> = emptyList(),
    val showOlder: Boolean = false,
)

/**
 * Splits [patchNotes] into "new since your last visit" and "older" against the persisted last-seen
 * version, captured once so the delta stays stable while we mark the newest version seen in the
 * background. Opening the page is what marks it seen.
 */
class PatchNotesViewModel(
    private val repository: PatchNotesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PatchNotesUiState())
    val state: StateFlow<PatchNotesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lastSeen = repository.lastSeenVersion.first()
            val (new, older) = splitPatchNotes(patchNotes, lastSeen)
            _state.update { it.copy(loading = false, newNotes = new, olderNotes = older) }
            patchNotes.firstOrNull()?.let { repository.markSeen(it.version) }
        }
    }

    fun toggleOlder() = _state.update { it.copy(showOlder = !it.showOlder) }
}
