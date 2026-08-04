package nl.jjt.vorfahrtfahrradcompanion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.Settings
import nl.jjt.vorfahrtfahrradcompanion.service.connection.ConnectionTester
import nl.jjt.vorfahrtfahrradcompanion.service.connection.ConnectionTestResult
import nl.jjt.vorfahrtfahrradcompanion.service.http.isAllowedUrl
import nl.jjt.vorfahrtfahrradcompanion.service.http.normalizeBaseUrl

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Running : ConnectionTestState
    data object Ok : ConnectionTestState
    data class Failed(val message: String) : ConnectionTestState
}

/**
 * [baseUrl] is the raw text as typed; [normalizedBaseUrl] is what gets stored.
 */
data class SettingsUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val connectionTest: ConnectionTestState = ConnectionTestState.Idle,
    val savedSettings: Settings? = null,
) {
    private val parsedBaseUrl: String? = normalizeBaseUrl(baseUrl)

    /** Parses fine, but plain http:// towards a server outside the local network. */
    val isBaseUrlInsecure: Boolean = parsedBaseUrl != null && !isAllowedUrl(parsedBaseUrl)
    val normalizedBaseUrl: String? = parsedBaseUrl.takeIf { !isBaseUrlInsecure }
    val isBaseUrlInvalid: Boolean = baseUrl.isNotBlank() && normalizedBaseUrl == null
    val canSubmit: Boolean = normalizedBaseUrl != null
    val hasUnsavedChanges: Boolean =
        normalizedBaseUrl != null && Settings(normalizedBaseUrl, username, password) != savedSettings
}

class SettingsViewModel(
    private val repository: SettingsStore,
    private val tester: ConnectionTester,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // One-shot seed: the raw text is owned by this ViewModel from here on, so later repository
        // emissions must not overwrite what is being typed.
        viewModelScope.launch {
            val saved = repository.settings.first()
            _state.update {
                it.copy(
                    baseUrl = saved.baseUrl,
                    username = saved.username,
                    password = saved.password,
                    savedSettings = saved,
                )
            }
        }
    }

    fun onBaseUrlChange(value: String) = update { copy(baseUrl = value) }

    fun onUsernameChange(value: String) = update { copy(username = value) }

    fun onPasswordChange(value: String) = update { copy(password = value) }

    fun save() {
        viewModelScope.launch { saveAndWait() }
    }

    /** Persists the current settings and updates the saved snapshot, suspending until done. */
    suspend fun saveAndWait() {
        val settings = currentSettings() ?: return
        repository.save(settings)
        _state.update { it.copy(savedSettings = settings) }
    }

    /** Reverts the editable fields back to the last-saved snapshot. */
    fun discardChanges() = _state.update { s ->
        val saved = s.savedSettings
        s.copy(
            baseUrl = saved?.baseUrl.orEmpty(),
            username = saved?.username.orEmpty(),
            password = saved?.password.orEmpty(),
            connectionTest = ConnectionTestState.Idle,
        )
    }

    fun testConnection() {
        val settings = currentSettings() ?: return
        viewModelScope.launch {
            _state.update { it.copy(connectionTest = ConnectionTestState.Running) }
            val result = tester.test(settings)
            _state.update {
                it.copy(
                    connectionTest = when (result) {
                        ConnectionTestResult.Ok -> ConnectionTestState.Ok
                        is ConnectionTestResult.Failed -> ConnectionTestState.Failed(result.message)
                    }
                )
            }
        }
    }

    /** Always the normalised base URL — never the raw text. */
    private fun currentSettings(): Settings? = _state.value.let { s ->
        s.normalizedBaseUrl?.let { Settings(it, s.username, s.password) }
    }

    /** Any edit invalidates a previous test result. */
    private fun update(edit: SettingsUiState.() -> SettingsUiState) {
        _state.update { it.edit().copy(connectionTest = ConnectionTestState.Idle) }
    }
}
