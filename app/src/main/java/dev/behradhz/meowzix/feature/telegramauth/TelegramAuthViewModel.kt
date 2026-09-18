package dev.behradhz.meowzix.feature.telegramauth

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import javax.inject.Inject

@HiltViewModel
class TelegramAuthViewModel @Inject constructor(
    private val repository: TelegramRepository,
) : ViewModel() {
    val state = repository.authState
    val musicSourceState = repository.musicSourceState

    fun submitPhoneNumber(value: String) = repository.submitPhoneNumber(value)
    fun submitCode(value: String) = repository.submitCode(value)
    fun submitPassword(value: String) = repository.submitPassword(value)
    fun submitEmailAddress(value: String) = repository.submitEmailAddress(value)
    fun submitEmailCode(value: String) = repository.submitEmailCode(value)
    fun register(firstName: String, lastName: String) = repository.register(firstName, lastName)
    fun logout() = repository.logout()
    fun clearError() = repository.clearError()
    fun refreshChats() = repository.refreshSelectableChats()
    fun setSourceSelected(chatId: Long, selected: Boolean) = repository.setMusicSourceSelected(chatId, selected)
    fun syncSelectedSources() = repository.syncSelectedSources()
    fun clearMusicSourceError() = repository.clearMusicSourceError()
}
