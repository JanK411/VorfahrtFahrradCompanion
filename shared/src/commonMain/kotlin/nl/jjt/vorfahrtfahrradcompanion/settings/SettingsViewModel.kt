package nl.jjt.vorfahrtfahrradcompanion.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    val normalizedBaseUrl: String? = normalizeBaseUrl(baseUrl)
    val isBaseUrlInvalid: Boolean = baseUrl.isNotBlank() && normalizedBaseUrl == null
    val canSubmit: Boolean = normalizedBaseUrl != null
}

class SettingsViewModel(
    private val repository: SettingsRepository,
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
                it.copy(baseUrl = saved.baseUrl, username = saved.username, password = saved.password)
            }
        }
    }

    fun onBaseUrlChange(value: String) = update { copy(baseUrl = value) }

    fun onUsernameChange(value: String) = update { copy(username = value) }

    fun onPasswordChange(value: String) = update { copy(password = value) }

    fun save() {
        val settings = currentSettings() ?: return
        viewModelScope.launch { repository.save(settings) }
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
