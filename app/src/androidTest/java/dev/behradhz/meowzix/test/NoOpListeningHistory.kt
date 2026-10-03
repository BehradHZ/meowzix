package dev.behradhz.meowzix.test

import dev.behradhz.meowzix.domain.history.*
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import java.util.UUID
import kotlinx.coroutines.flow.flowOf

class NoOpListeningHistory : ListeningHistoryRepository {
    override fun observeEvents() = flowOf(emptyList<ListeningEvent>())
    override fun observeTrackStats() = flowOf(emptyList<TrackPreferenceStats>())
    override suspend fun startPlayback(trackId: UUID, initiatedBy: PlaybackInitiator, mode: PlaybackMode) = UUID.randomUUID()
    override suspend fun finalizePlayback(playbackInstanceId: UUID, positionMs: Long, durationMs: Long, intentionalSkip: Boolean) {}
    override suspend fun recordSeek(playbackInstanceId: UUID, positionMs: Long, durationMs: Long) {}
    override suspend fun clear() {}
    override suspend fun resetPersonalization() {}
}
