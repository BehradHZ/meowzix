package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.settings.LibraryDisplaySettings
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryDisplayViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.libraryDisplaySettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryDisplaySettings(),
    )

    fun setSortMode(mode: LibrarySortMode) = viewModelScope.launch {
        settingsRepository.setLibrarySortMode(mode)
    }

    fun setGroupMode(mode: LibraryGroupMode) = viewModelScope.launch {
        settingsRepository.setLibraryGroupMode(mode)
    }
}
