package dev.behradhz.meowzix.feature.nowplaying

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsRepository
import dev.behradhz.meowzix.domain.lyrics.LyricsTiming
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import dev.behradhz.meowzix.domain.playback.PlaybackController
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowPlayingLyricsState(
    val trackId: UUID? = null,
    val trackLyrics: TrackLyrics? = null,
    val activeLineIndex: Int = -1,
    val errorMessage: String? = null,
) {
    val selected: LyricsVersion? get() = trackLyrics?.selected
    val effectiveLines get() = selected?.effectiveLines().orEmpty()
    val hasLyrics: Boolean get() = selected != null
    val isTimed: Boolean get() = selected?.parsed?.contentType == LyricsContentType.TIMED
}

@HiltViewModel
class NowPlayingLyricsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackController: PlaybackController,
    private val repository: LyricsRepository,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)

    private val trackLyrics = playbackController.state
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId == null) flowOf(null)
            else repository.observe(trackId).map<TrackLyrics, TrackLyrics?> { it }
        }

    val state = combine(playbackController.state, trackLyrics, error) { playback, lyrics, message ->
        val trackId = playback.currentTrack?.id
        val selected = lyrics?.takeIf { it.trackId == trackId }?.selected
        val effective = selected?.effectiveLines().orEmpty()
        val active = if (selected?.parsed?.contentType == LyricsContentType.TIMED) {
            LyricsTiming.activeLineIndex(effective, playback.positionMs)
        } else {
            -1
        }
        NowPlayingLyricsState(
            trackId = trackId,
            trackLyrics = lyrics?.takeIf { it.trackId == trackId },
            activeLineIndex = active,
            errorMessage = message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NowPlayingLyricsState(),
    )

    fun importLrc(uri: Uri) {
        val trackId = state.value.trackId ?: return
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { readBoundedUtf8(uri) }
                repository.importLrc(trackId, text, uri.lastPathSegment)
            }.onSuccess {
                error.value = null
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                error.value = failure.message ?: "Unable to import lyrics"
            }
        }
    }

    fun pastePlainLyrics(text: String) {
        val trackId = state.value.trackId ?: return
        viewModelScope.launch {
            runCatching { repository.savePlainText(trackId, text) }
                .onSuccess { error.value = null }
                .onFailure { error.value = it.message ?: "Unable to save lyrics" }
        }
    }

    fun selectVersion(versionId: UUID) {
        val trackId = state.value.trackId ?: return
        viewModelScope.launch {
            runCatching { repository.selectVersion(trackId, versionId) }
                .onFailure { error.value = it.message ?: "Unable to select lyrics" }
        }
    }

    fun adjustDelay(deltaMs: Long) {
        val selected = state.value.selected ?: return
        viewModelScope.launch {
            val next = (selected.userDelayMs + deltaMs).coerceIn(-30_000L, 30_000L)
            runCatching { repository.setUserDelay(selected.id, next) }
                .onFailure { error.value = it.message ?: "Unable to adjust lyrics timing" }
        }
    }

    fun seekToLine(index: Int) {
        val line = state.value.effectiveLines.getOrNull(index) ?: return
        playbackController.seekTo(line.timeMs.coerceAtLeast(0L))
    }

    fun clearError() { error.value = null }

    private fun readBoundedUtf8(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: error("Unable to open lyrics file")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_LRC_BYTES) { "Lyrics file is too large" }
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }
    }

    private companion object {
        const val MAX_LRC_BYTES = LrcParser.MAX_CHARS * 4
    }
}
