package nl.jjt.vorfahrtfahrradcompanion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.CategorizationVariant

/** [categorization] is null until the row has been read; nothing is drawn for it before then. */
data class SettingsUiState(val categorization: CategorizationVariant? = null)

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    val state: StateFlow<SettingsUiState> = store.categorization
        .map { SettingsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun choose(variant: CategorizationVariant) {
        viewModelScope.launch { store.saveCategorization(variant) }
    }
}
