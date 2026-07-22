package nl.jjt.vorfahrtfahrradcompanion.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.jjt.vorfahrtfahrradcompanion.navigation.LeaveGuard
import nl.jjt.vorfahrtfahrradcompanion.navigation.rememberConfirmPrompt
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenPatchNotes: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val confirmLeave = rememberConfirmPrompt { answer ->
        UnsavedChangesDialog(
            saving = saving,
            onSave = {
                saving = true
                scope.launch {
                    viewModel.saveAndWait()
                    saving = false
                    answer(true)
                }
            },
            onDiscard = {
                viewModel.discardChanges()
                answer(true)
            },
            onDismiss = { answer(false) },
        )
    }

    LeaveGuard { !viewModel.state.value.hasUnsavedChanges || confirmLeave() }

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

        ConnectionTester(
            state = state.connectionTest,
            enabled = state.canSubmit,
            onTest = viewModel::testConnection,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenPatchNotes)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("What's New", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }

        Spacer(Modifier.weight(1f))

        if (state.hasUnsavedChanges) {
            Button(
                onClick = viewModel::save,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    saving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Unsaved changes") },
        text = { Text("You have unsaved settings. Save them before leaving?") },
        confirmButton = {
            TextButton(enabled = !saving, onClick = onSave) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDiscard) { Text("Discard") }
        },
    )
}

/**
 * A "Test connection" row whose action button morphs into a green tick on success and a red cross on
 * failure, while the label keeps it obvious what the button does. Stays tappable so the test can be rerun.
 */
@Composable
private fun ConnectionTester(
    state: ConnectionTestState,
    enabled: Boolean,
    onTest: () -> Unit,
) {
    val ok = Color(0xFF2E7D32)
    val onOk = Color.White
    val error = MaterialTheme.colorScheme.error
    val onError = MaterialTheme.colorScheme.onError
    val neutral = MaterialTheme.colorScheme.secondaryContainer
    val onNeutral = MaterialTheme.colorScheme.onSecondaryContainer

    val container by animateColorAsState(
        when (state) {
            ConnectionTestState.Ok -> ok
            is ConnectionTestState.Failed -> error
            else -> neutral
        },
        tween(300),
    )
    val content by animateColorAsState(
        when (state) {
            ConnectionTestState.Ok -> onOk
            is ConnectionTestState.Failed -> onError
            else -> onNeutral
        },
        tween(300),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Test connection", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when (state) {
                    ConnectionTestState.Idle -> "Not tested yet"
                    ConnectionTestState.Running -> "Testing…"
                    ConnectionTestState.Ok -> "Connection OK"
                    is ConnectionTestState.Failed -> state.message
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    ConnectionTestState.Ok -> ok
                    is ConnectionTestState.Failed -> error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        FilledIconButton(
            onClick = onTest,
            enabled = enabled && state != ConnectionTestState.Running,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container,
                contentColor = content,
            ),
            modifier = Modifier.size(56.dp),
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (scaleIn(tween(220)) + fadeIn(tween(220))) togetherWith
                        (scaleOut(tween(220)) + fadeOut(tween(220)))
                },
                label = "connection-test-icon",
            ) { s ->
                when (s) {
                    ConnectionTestState.Idle ->
                        Icon(Icons.Default.Refresh, contentDescription = "Test connection")

                    ConnectionTestState.Running ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = content,
                        )

                    ConnectionTestState.Ok ->
                        Icon(Icons.Default.Check, contentDescription = "Connection OK")

                    is ConnectionTestState.Failed ->
                        Icon(Icons.Default.Close, contentDescription = "Connection failed")
                }
            }
        }
    }
}
