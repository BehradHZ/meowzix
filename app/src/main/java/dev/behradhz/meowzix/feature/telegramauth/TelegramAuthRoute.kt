package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.telegram.TelegramAuthState
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState

@Composable
fun TelegramAuthRoute(
    onBack: () -> Unit,
    viewModel: TelegramAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val musicSourceState by viewModel.musicSourceState.collectAsStateWithLifecycle()
    TelegramAuthScreen(
        state = state,
        musicSourceState = musicSourceState,
        onBack = onBack,
        onPhoneNumber = viewModel::submitPhoneNumber,
        onCode = viewModel::submitCode,
        onPassword = viewModel::submitPassword,
        onEmailAddress = viewModel::submitEmailAddress,
        onEmailCode = viewModel::submitEmailCode,
        onRegister = viewModel::register,
        onLogout = viewModel::logout,
        onClearError = viewModel::clearError,
        onRefreshChats = viewModel::refreshChats,
        onToggleSource = viewModel::setSourceSelected,
        onSync = viewModel::syncSelectedSources,
        onClearMusicSourceError = viewModel::clearMusicSourceError,
    )
}

@Composable
private fun TelegramAuthScreen(
    state: TelegramAuthState,
    musicSourceState: TelegramMusicSourceState,
    onBack: () -> Unit,
    onPhoneNumber: (String) -> Unit,
    onCode: (String) -> Unit,
    onPassword: (String) -> Unit,
    onEmailAddress: (String) -> Unit,
    onEmailCode: (String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onClearError: () -> Unit,
    onRefreshChats: () -> Unit,
    onToggleSource: (Long, Boolean) -> Unit,
    onSync: () -> Unit,
    onClearMusicSourceError: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onBack) { Text("Back") }
                Text("Telegram", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(1.dp))
            }
            Spacer(Modifier.height(24.dp))

            state.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onClearError) { Text("Dismiss") }
                Spacer(Modifier.height(12.dp))
            }

            when (val step = state.step) {
                TelegramAuthStep.Initializing -> ProgressState("Starting Telegram…")
                TelegramAuthStep.ConfigurationRequired -> ConfigurationRequiredState()
                TelegramAuthStep.WaitPhoneNumber -> SingleValueForm(
                    title = "Sign in to Telegram",
                    guidance = "Enter your phone number with country code.",
                    label = "Phone number",
                    submitLabel = "Continue",
                    enabled = !state.isSubmitting,
                    keyboardType = KeyboardType.Phone,
                    onSubmit = onPhoneNumber,
                )
                is TelegramAuthStep.WaitCode -> SingleValueForm(
                    title = "Authentication code",
                    guidance = "Enter the code sent for ${step.phoneNumber}.",
                    label = "Code",
                    submitLabel = "Verify",
                    enabled = !state.isSubmitting,
                    onSubmit = onCode,
                )
                is TelegramAuthStep.WaitPassword -> SingleValueForm(
                    title = "Two-step verification",
                    guidance = buildString {
                        append("Enter your Telegram password.")
                        if (step.hint.isNotBlank()) append(" Hint: ${step.hint}")
                    },
                    label = "Password",
                    submitLabel = "Verify",
                    enabled = !state.isSubmitting,
                    isPassword = true,
                    onSubmit = onPassword,
                )
                TelegramAuthStep.WaitEmailAddress -> SingleValueForm(
                    title = "Email address",
                    guidance = "Telegram requires an email address for this login.",
                    label = "Email",
                    submitLabel = "Continue",
                    enabled = !state.isSubmitting,
                    keyboardType = KeyboardType.Email,
                    onSubmit = onEmailAddress,
                )
                is TelegramAuthStep.WaitEmailCode -> SingleValueForm(
                    title = "Email code",
                    guidance = "Enter the ${step.codeLength}-character code sent to ${step.emailPattern}.",
                    label = "Email code",
                    submitLabel = "Verify",
                    enabled = !state.isSubmitting,
                    onSubmit = onEmailCode,
                )
                TelegramAuthStep.WaitRegistration -> RegistrationForm(
                    enabled = !state.isSubmitting,
                    onSubmit = onRegister,
                )
                is TelegramAuthStep.WaitOtherDeviceConfirmation -> OtherDeviceConfirmation(step.link)
                TelegramAuthStep.Ready -> ReadyState(
                    enabled = !state.isSubmitting,
                    sourceState = musicSourceState,
                    onRefreshChats = onRefreshChats,
                    onToggleSource = onToggleSource,
                    onSync = onSync,
                    onClearSourceError = onClearMusicSourceError,
                    onLogout = onLogout,
                )
                TelegramAuthStep.LoggingOut -> ProgressState("Logging out…")
                TelegramAuthStep.Closing -> ProgressState("Closing Telegram session…")
                TelegramAuthStep.Closed -> ProgressState("Telegram session closed")
                is TelegramAuthStep.Unsupported -> Text(step.reason, color = MaterialTheme.colorScheme.error)
            }
            if (state.isSubmitting) CircularProgressIndicator(Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun SingleValueForm(
    title: String,
    guidance: String,
    label: String,
    submitLabel: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onSubmit: (String) -> Unit,
) {
    var value by remember(title) { mutableStateOf("") }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(guidance, modifier = Modifier.padding(vertical = 12.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            val submitted = value
            value = ""
            onSubmit(submitted)
        },
        enabled = enabled,
        modifier = Modifier.padding(top = 16.dp),
    ) { Text(submitLabel) }
}

@Composable
private fun RegistrationForm(enabled: Boolean, onSubmit: (String, String) -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    Text("Create Telegram profile", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = firstName,
        onValueChange = { firstName = it },
        label = { Text("First name") },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    OutlinedTextField(
        value = lastName,
        onValueChange = { lastName = it },
        label = { Text("Last name") },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        onClick = {
            val submittedFirstName = firstName
            val submittedLastName = lastName
            firstName = ""
            lastName = ""
            onSubmit(submittedFirstName, submittedLastName)
        },
        enabled = enabled,
        modifier = Modifier.padding(top = 16.dp),
    ) { Text("Register") }
}

@Composable
private fun ConfigurationRequiredState() {
    Text("Telegram credentials required", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Set MEOWZIX_TELEGRAM_API_ID and MEOWZIX_TELEGRAM_API_HASH, then rebuild the app.",
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun OtherDeviceConfirmation(link: String) {
    val uriHandler = LocalUriHandler.current
    Text("Confirm on another device", style = MaterialTheme.typography.headlineSmall)
    Text("Telegram requires confirmation from an already signed-in device.", modifier = Modifier.padding(12.dp))
    Button(onClick = { uriHandler.openUri(link) }) { Text("Open confirmation") }
}

@Composable
private fun ReadyState(
    enabled: Boolean,
    sourceState: TelegramMusicSourceState,
    onRefreshChats: () -> Unit,
    onToggleSource: (Long, Boolean) -> Unit,
    onSync: () -> Unit,
    onClearSourceError: () -> Unit,
    onLogout: () -> Unit,
) {
    Text("Telegram connected", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Choose the chats Meowzix should treat as music sources. Only selected chats are indexed.",
        modifier = Modifier.padding(vertical = 10.dp),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onRefreshChats,
            enabled = enabled && !sourceState.isLoadingChats && !sourceState.isSyncing,
        ) { Text("Refresh chats") }
        Button(
            onClick = onSync,
            enabled = enabled && sourceState.selectedChatIds.isNotEmpty() && !sourceState.isSyncing,
        ) { Text(if (sourceState.isSyncing) "Syncing…" else "Sync music") }
        TextButton(onClick = onLogout, enabled = enabled && !sourceState.isSyncing) { Text("Log out") }
    }

    sourceState.errorMessage?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        TextButton(onClick = onClearSourceError) { Text("Dismiss") }
    }

    sourceState.lastSyncResult?.let { result ->
        Text(
            "Last sync: ${result.tracksImported} imported, ${result.tracksUpdated} updated, ${result.sourcesMarkedMissing} missing.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }

    when {
        sourceState.isLoadingChats -> {
            CircularProgressIndicator(Modifier.padding(top = 24.dp))
            Text("Loading chats…", modifier = Modifier.padding(top = 8.dp))
        }
        sourceState.chats.isEmpty() -> {
            Text(
                "No chats loaded yet. Refresh the list after Telegram finishes loading your chat list.",
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        else -> {
            Text(
                "Music sources",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                items(sourceState.chats, key = { it.chatId }) { chat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(chat.kind.displayName(), style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = chat.chatId in sourceState.selectedChatIds,
                            onCheckedChange = { checked -> onToggleSource(chat.chatId, checked) },
                            enabled = enabled && !sourceState.isSyncing,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun TelegramChatKind.displayName(): String = when (this) {
    TelegramChatKind.SAVED_MESSAGES -> "Saved Messages"
    TelegramChatKind.PRIVATE -> "Private chat"
    TelegramChatKind.BASIC_GROUP -> "Group"
    TelegramChatKind.SUPERGROUP_OR_CHANNEL -> "Channel or supergroup"
    TelegramChatKind.SECRET -> "Secret chat"
}

@Composable
private fun ProgressState(message: String) {
    CircularProgressIndicator()
    Text(message, modifier = Modifier.padding(top = 12.dp))
}
