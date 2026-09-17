package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.telegram.TelegramAuthState
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep

@Composable
fun TelegramAuthRoute(
    onBack: () -> Unit,
    viewModel: TelegramAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TelegramAuthScreen(
        state = state,
        onBack = onBack,
        onPhoneNumber = viewModel::submitPhoneNumber,
        onCode = viewModel::submitCode,
        onPassword = viewModel::submitPassword,
        onEmailAddress = viewModel::submitEmailAddress,
        onEmailCode = viewModel::submitEmailCode,
        onRegister = viewModel::register,
        onLogout = viewModel::logout,
        onClearError = viewModel::clearError,
    )
}

@Composable
private fun TelegramAuthScreen(
    state: TelegramAuthState,
    onBack: () -> Unit,
    onPhoneNumber: (String) -> Unit,
    onCode: (String) -> Unit,
    onPassword: (String) -> Unit,
    onEmailAddress: (String) -> Unit,
    onEmailCode: (String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onClearError: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text("Telegram", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Personal cloud music source",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Cloud, contentDescription = null)
                    }
                }
            }

            state.errorMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                        Text(
                            message,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val step = state.step) {
                        TelegramAuthStep.Initializing -> ProgressState("Starting Telegram…")
                        TelegramAuthStep.ConfigurationRequired -> MessageState(
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            title = "Telegram setup required",
                            message = "This development build needs its Telegram API configuration before sign-in can start.",
                        )
                        TelegramAuthStep.WaitPhoneNumber -> SingleValueForm(
                            icon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            title = "Connect Telegram",
                            guidance = "Enter the phone number attached to your Telegram account, including the country code.",
                            label = "Phone number",
                            submitLabel = "Continue",
                            enabled = !state.isSubmitting,
                            keyboardType = KeyboardType.Phone,
                            onSubmit = onPhoneNumber,
                        )
                        is TelegramAuthStep.WaitCode -> SingleValueForm(
                            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                            title = "Authentication code",
                            guidance = "Enter the code Telegram sent for ${step.phoneNumber}.",
                            label = "Code",
                            submitLabel = "Verify",
                            enabled = !state.isSubmitting,
                            keyboardType = KeyboardType.Number,
                            onSubmit = onCode,
                        )
                        is TelegramAuthStep.WaitPassword -> SingleValueForm(
                            icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
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
                            icon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                            title = "Email confirmation",
                            guidance = "Telegram requires an email address for this sign-in.",
                            label = "Email address",
                            submitLabel = "Continue",
                            enabled = !state.isSubmitting,
                            keyboardType = KeyboardType.Email,
                            onSubmit = onEmailAddress,
                        )
                        is TelegramAuthStep.WaitEmailCode -> SingleValueForm(
                            icon = { Icon(Icons.Rounded.Email, contentDescription = null) },
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
                            onLogout = onLogout,
                        )
                        TelegramAuthStep.LoggingOut -> ProgressState("Logging out…")
                        TelegramAuthStep.Closing -> ProgressState("Closing Telegram session…")
                        TelegramAuthStep.Closed -> ProgressState("Telegram session closed")
                        is TelegramAuthStep.Unsupported -> MessageState(
                            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
                            title = "Telegram needs attention",
                            message = step.reason,
                        )
                    }
                    if (state.isSubmitting) {
                        CircularProgressIndicator(Modifier.padding(top = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleValueForm(
    icon: @Composable () -> Unit,
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
    MessageState(icon = icon, title = title, message = guidance)
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            val submitted = value
            value = ""
            onSubmit(submitted)
        },
        enabled = enabled && value.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Text(submitLabel)
    }
}

@Composable
private fun RegistrationForm(enabled: Boolean, onSubmit: (String, String) -> Unit) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    MessageState(
        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
        title = "Create Telegram profile",
        message = "Telegram needs a profile name to finish account registration.",
    )
    OutlinedTextField(
        value = firstName,
        onValueChange = { firstName = it },
        label = { Text("First name") },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = lastName,
        onValueChange = { lastName = it },
        label = { Text("Last name") },
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
    Button(
        onClick = {
            val first = firstName
            val last = lastName
            firstName = ""
            lastName = ""
            onSubmit(first, last)
        },
        enabled = enabled && firstName.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Text("Register")
    }
}

@Composable
private fun OtherDeviceConfirmation(link: String) {
    val uriHandler = LocalUriHandler.current
    MessageState(
        icon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
        title = "Confirm on another device",
        message = "Telegram requires approval from a device where you're already signed in.",
    )
    Button(
        onClick = { uriHandler.openUri(link) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Open confirmation")
    }
}

@Composable
private fun ReadyState(enabled: Boolean, onLogout: () -> Unit) {
    MessageState(
        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
        title = "Telegram connected",
        message = "Your TDLib session is ready. Music source selection arrives with the next Telegram library increment.",
    )
    OutlinedButton(
        onClick = onLogout,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Log out")
    }
}

@Composable
private fun MessageState(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 18.dp),
    )
    Text(
        message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
    )
}

@Composable
private fun ProgressState(message: String) {
    CircularProgressIndicator()
    Spacer(Modifier.height(18.dp))
    Text(message, textAlign = TextAlign.Center)
}
