package dev.behradhz.meowzix.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryRow(val trackId: UUID, val title: String, val type: ListeningEventType, val occurredAt: Instant)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: ListeningHistoryRepository,
    library: MusicLibraryRepository,
    private val settings: SettingsRepository,
    private val maintenance: dev.behradhz.meowzix.domain.recommendation.PersonalizationMaintenance,
) : ViewModel() {
    val rows = combine(history.observeEvents(), library.observeTracks()) { events, tracks ->
        val titles = tracks.associate { it.id to it.title }
        events.map { HistoryRow(it.trackId, titles[it.trackId] ?: "Unknown track", it.type, it.occurredAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val privacy = settings.networkPlaybackSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings())

    private val _debugReports = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val debugReports = _debugReports.asSharedFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage = _operationMessage.asStateFlow()
    private fun action(message: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); _operationMessage.value = message }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _operationMessage.value = "Unable to complete this action. Please try again." }
    }
    fun setHistoryEnabled(enabled: Boolean) = action("Listening history setting updated.") { settings.setListeningHistoryEnabled(enabled) }
    fun clear() = action("Listening history cleared.") { history.clear() }
    fun resetPersonalization() = action("Learning reset. Your listening history is kept.") { history.resetPersonalization() }
    fun rebuildPersonalization() = action("Learning from stored history has been scheduled.") { maintenance.rebuild() }
    fun analyzeAudio() = action("Local audio analysis has been scheduled.") { maintenance.analyzeAvailableAudio() }
    fun exportPersonalizationDebugReport() = action("Report ready.") { _debugReports.emit(maintenance.debugReport()) }
}
