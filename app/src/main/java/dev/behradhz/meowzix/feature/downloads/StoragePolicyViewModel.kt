package dev.behradhz.meowzix.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import dev.behradhz.meowzix.domain.downloads.ManagedStorageUsage
import dev.behradhz.meowzix.domain.settings.DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.StoragePolicySettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StoragePolicyViewModel @Inject constructor(
    private val storage: ManagedStorageRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _usage = MutableStateFlow<ManagedStorageUsage?>(null)
    val usage: StateFlow<ManagedStorageUsage?> = _usage.asStateFlow()

    val settings = settingsRepository.storagePolicySettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StoragePolicySettings(DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES),
    )

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _usage.value = storage.usage()
    }

    fun setBudget(bytes: Long) = viewModelScope.launch {
        settingsRepository.setTemporaryCacheBudgetBytes(bytes)
        _usage.value = storage.reconcileAndEnforceBudget()
    }

    fun clearTemporaryCache() = viewModelScope.launch {
        _usage.value = storage.clearTemporaryCache()
    }
}
