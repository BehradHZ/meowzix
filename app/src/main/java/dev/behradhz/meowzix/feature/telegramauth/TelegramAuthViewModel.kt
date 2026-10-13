package dev.behradhz.meowzix.feature.telegramauth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.telegram.TelegramProxyDraft
import javax.inject.Inject

@HiltViewModel
class TelegramAuthViewModel @Inject constructor(
    private val repository: TelegramRepository,
) : ViewModel() {
    val state = repository.authState
    val musicSourceState = repository.musicSourceState
    val proxyState = repository.proxyState

    fun submitPhoneNumber(value: String) = repository.submitPhoneNumber(value)
    fun submitCode(value: String) = repository.submitCode(value)
    fun submitPassword(value: String) = repository.submitPassword(value)
    fun submitEmailAddress(value: String) = repository.submitEmailAddress(value)
    fun submitEmailCode(value: String) = repository.submitEmailCode(value)
    fun register(firstName: String, lastName: String) = repository.register(firstName, lastName)
    fun logout() = repository.logout()
    fun resetTelegram() = repository.resetClient()
    fun clearError() = repository.clearError()

    fun refreshProxies() = repository.refreshProxies()
    fun addProxy(draft: TelegramProxyDraft) = repository.addProxy(draft)
    fun importProxyLink(link: String) = repository.importProxyLink(link)
    fun enableProxy(id: Int) = repository.enableProxy(id)
    fun disableProxy() = repository.disableProxy()
    fun removeProxy(id: Int) = repository.removeProxy(id)
    fun pingProxy(id: Int) = repository.pingProxy(id)
    fun clearProxyError() = repository.clearProxyError()

    fun refreshMusicSources() = repository.refreshSelectableChats()
    fun setMusicSourceSelected(chatId: Long, selected: Boolean) =
        repository.setMusicSourceSelected(chatId, selected)
    fun syncSelectedSources() = repository.syncSelectedSources()
    fun clearMusicSourceError() = repository.clearMusicSourceError()
}
