package dev.behradhz.meowzix.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.data.recommendation.PersonalizationDebugReportBuilder
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
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
    private val debugReportBuilder: PersonalizationDebugReportBuilder,
) : ViewModel() {
    val rows = combine(history.observeEvents(), library.observeTracks()) { events, tracks ->
        val titles = tracks.associate { it.id to it.title }
        events.map { HistoryRow(it.trackId, titles[it.trackId] ?: "Unknown track", it.type, it.occurredAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val privacy = settings.networkPlaybackSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dev.behradhz.meowzix.domain.settings.NetworkPlaybackSettings())

    private val _debugReports = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val debugReports = _debugReports.asSharedFlow()

    fun setHistoryEnabled(enabled: Boolean) = viewModelScope.launch { settings.setListeningHistoryEnabled(enabled) }
    fun clear() = viewModelScope.launch { history.clear() }
    fun resetPersonalization() = viewModelScope.launch { history.resetPersonalization() }

    fun exportPersonalizationDebugReport() = viewModelScope.launch {
        _debugReports.emit(debugReportBuilder.buildText())
    }
}
