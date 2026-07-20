package nl.jjt.vorfahrtfahrradcompanion.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::onBaseUrlChange,
            label = { Text("Base URL") },
            placeholder = { Text("http://192.168.178.123:8080") },
            supportingText = {
                Text(
                    if (state.isBaseUrlInvalid) {
                        "Enter a valid URL, e.g. http://192.168.178.123:8080 or https://vorfahrt.example.com"
                    } else {
                        state.normalizedBaseUrl.orEmpty()
                    }
                )
            },
            isError = state.isBaseUrlInvalid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = viewModel::testConnection, enabled = state.canSubmit) {
                Text("Test connection")
            }
            when (val test = state.connectionTest) {
                ConnectionTestState.Idle -> {}
                ConnectionTestState.Running -> Text("Testing…")
                ConnectionTestState.Ok -> Text("Connection OK", color = MaterialTheme.colorScheme.primary)
                is ConnectionTestState.Failed -> Text(test.message, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.hasUnsavedChanges) {
            Button(onClick = viewModel::save, enabled = state.canSubmit) {
                Text("Save")
            }
        }
    }
}
