package dev.behradhz.meowzix.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.playback.EqualizerPreset
import dev.behradhz.meowzix.domain.playback.EqualizerRepository
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val repository: EqualizerRepository,
) : ViewModel() {
    val state = repository.state

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun setBandLevel(index: Int, levelDb: Float) {
        viewModelScope.launch { repository.setBandLevel(index, levelDb) }
    }

    fun applyPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        viewModelScope.launch { repository.applyPreset(preset) }
    }

    fun resetFlat() {
        viewModelScope.launch { repository.resetFlat() }
    }
}
