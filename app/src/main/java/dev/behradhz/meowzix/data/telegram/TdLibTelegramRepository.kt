package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.BuildConfig
import dev.behradhz.meowzix.domain.telegram.TelegramAuthState
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Singleton
class TdLibTelegramRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TelegramRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authState = MutableStateFlow(TelegramAuthState())
    override val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()
    private var client: TdLibClientAdapter? = null
    private var restartAfterClose = false

    init {
        startClient()
    }

    override fun submitPhoneNumber(phoneNumber: String) = submit(
        value = phoneNumber,
        emptyMessage = "Enter a phone number including its country code.",
    ) { TdApi.SetAuthenticationPhoneNumber(it, TdApi.PhoneNumberAuthenticationSettings()) }

    override fun submitCode(code: String) = submit(code, "Enter the authentication code.") {
        TdApi.CheckAuthenticationCode(it)
    }

    override fun submitPassword(password: String) = submit(password, "Enter your two-step verification password.") {
        TdApi.CheckAuthenticationPassword(it)
    }

    override fun submitEmailAddress(emailAddress: String) = submit(emailAddress, "Enter an email address.") {
        TdApi.SetAuthenticationEmailAddress(it)
    }

    override fun submitEmailCode(code: String) = submit(code, "Enter the email authentication code.") {
        TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(it))
    }

    override fun register(firstName: String, lastName: String) {
        if (firstName.isBlank()) {
            showError("Enter your first name.")
            return
        }
        submitRequest { TdApi.RegisterUser(firstName.trim(), lastName.trim(), false) }
    }

    override fun logout() {
        restartAfterClose = true
        submitRequest { TdApi.LogOut() }
    }

    override fun clearError() {
        _authState.update { it.copy(errorMessage = null) }
    }

    private fun startClient() {
        val apiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull()?.takeIf { it > 0 }
        if (apiId == null || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _authState.value = TelegramAuthState(step = TelegramAuthStep.ConfigurationRequired)
            return
        }
        _authState.value = TelegramAuthState(step = TelegramAuthStep.Initializing)
        scope.launch {
            runCatching { TdLibClientAdapter() }
                .onSuccess { adapter ->
                    client = adapter
                    launch { adapter.updates.collect(::handleUpdate) }
                    launch { adapter.failures.collect { showError(safeMessage(it)) } }
                }
                .onFailure { showError("TDLib could not start: ${safeMessage(it)}") }
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        if (update !is TdApi.UpdateAuthorizationState) return
        val authorizationState = update.authorizationState
        if (authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
            submitParameters()
            return
        }
        val step = authorizationState.toDomainStep()
        _authState.value = TelegramAuthState(step = step)
        if (step == TelegramAuthStep.Closed && restartAfterClose) {
            restartAfterClose = false
            client = null
            startClient()
        }
    }

    private fun submitParameters() {
        val apiId = requireNotNull(BuildConfig.TELEGRAM_API_ID.toIntOrNull())
        val root = File(context.noBackupFilesDir, "tdlib").apply { mkdirs() }
        val databaseDirectory = File(root, "database").apply { mkdirs() }
        val filesDirectory = File(root, "files").apply { mkdirs() }
        submitRequest {
            TdApi.SetTdlibParameters(
                false,
                databaseDirectory.absolutePath,
                filesDirectory.absolutePath,
                byteArrayOf(),
                true,
                true,
                false,
                false,
                apiId,
                BuildConfig.TELEGRAM_API_HASH,
                Locale.getDefault().toLanguageTag(),
                Build.MODEL,
                Build.VERSION.RELEASE,
                BuildConfig.VERSION_NAME,
            )
        }
    }

    private fun <R : TdApi.Object> submit(
        value: String,
        emptyMessage: String,
        request: (String) -> TdApi.Function<R>,
    ) {
        if (value.isBlank()) {
            showError(emptyMessage)
            return
        }
        submitRequest { request(value.trim()) }
    }

    private fun <R : TdApi.Object> submitRequest(request: () -> TdApi.Function<R>) {
        val activeClient = client
        if (activeClient == null) {
            showError("Telegram is not ready yet.")
            return
        }
        val stepAtRequest = _authState.value.step
        _authState.update { it.copy(isSubmitting = true, errorMessage = null) }
        scope.launch {
            runCatching { activeClient.send(request()) }
                .onSuccess {
                    _authState.update { current ->
                        if (current.step == stepAtRequest) current.copy(isSubmitting = false) else current
                    }
                }
                .onFailure { error ->
                    _authState.update { current ->
                        current.copy(isSubmitting = false, errorMessage = safeMessage(error))
                    }
                }
        }
    }

    private fun showError(message: String) {
        _authState.update { it.copy(isSubmitting = false, errorMessage = message) }
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: "Telegram request failed."
}

internal fun TdApi.AuthorizationState.toDomainStep(): TelegramAuthStep = when (this) {
    is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramAuthStep.WaitPhoneNumber
    is TdApi.AuthorizationStateWaitCode -> TelegramAuthStep.WaitCode(
        phoneNumber = codeInfo.phoneNumber,
        timeoutSeconds = codeInfo.timeout,
    )
    is TdApi.AuthorizationStateWaitPassword -> TelegramAuthStep.WaitPassword(
        hint = passwordHint,
        hasRecoveryEmail = hasRecoveryEmailAddress,
        recoveryEmailPattern = recoveryEmailAddressPattern,
    )
    is TdApi.AuthorizationStateWaitEmailAddress -> TelegramAuthStep.WaitEmailAddress
    is TdApi.AuthorizationStateWaitEmailCode -> TelegramAuthStep.WaitEmailCode(
        emailPattern = codeInfo.emailAddressPattern,
        codeLength = codeInfo.length,
    )
    is TdApi.AuthorizationStateWaitRegistration -> TelegramAuthStep.WaitRegistration
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
        TelegramAuthStep.WaitOtherDeviceConfirmation(link)
    is TdApi.AuthorizationStateReady -> TelegramAuthStep.Ready
    is TdApi.AuthorizationStateLoggingOut -> TelegramAuthStep.LoggingOut
    is TdApi.AuthorizationStateClosing -> TelegramAuthStep.Closing
    is TdApi.AuthorizationStateClosed -> TelegramAuthStep.Closed
    is TdApi.AuthorizationStateWaitPremiumPurchase ->
        TelegramAuthStep.Unsupported("Telegram Premium is required to complete this login.")
    is TdApi.AuthorizationStateWaitTdlibParameters -> TelegramAuthStep.Initializing
    else -> TelegramAuthStep.Unsupported("This Telegram authorization step is not supported yet.")
}
