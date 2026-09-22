package dev.behradhz.meowzix.domain.history

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ListeningEventType { PLAY_STARTED, PLAY_COMPLETED, PLAY_STOPPED, SKIPPED_EARLY, SKIPPED_LATE, MANUAL_SELECTED, AUTO_SELECTED, REPLAYED, FAVORITED, UNFAVORITED, SEEKED }
enum class PlaybackInitiator { USER, PURE_SHUFFLE, SMART_SHUFFLE, QUEUE, SYSTEM_RESUME }
enum class TimeBucket { EARLY_MORNING, MORNING, AFTERNOON, EVENING, NIGHT, LATE_NIGHT }

data class ListeningEvent(
    val id: UUID,
    val trackId: UUID,
    val type: ListeningEventType,
    val occurredAt: Instant,
    val localHour: Int,
    val dayOfWeek: DayOfWeek,
    val timeBucket: TimeBucket,
    val positionMs: Long?,
    val durationMs: Long?,
    val completionRatio: Double?,
    val initiatedBy: PlaybackInitiator,
    val playbackMode: PlaybackMode,
)

data class TrackPreferenceStats(
    val trackId: UUID,
    val totalStarts: Int,
    val totalCompletions: Int,
    val earlySkips: Int,
    val lateSkips: Int,
    val manualSelections: Int,
    val replays: Int,
    val lastPlayedAt: Instant?,
)

interface ListeningHistoryRepository {
    fun observeEvents(): Flow<List<ListeningEvent>>
    fun observeTrackStats(): Flow<List<TrackPreferenceStats>>
    suspend fun startPlayback(trackId: UUID, initiatedBy: PlaybackInitiator, mode: PlaybackMode): UUID
    suspend fun finalizePlayback(playbackInstanceId: UUID, positionMs: Long, durationMs: Long, intentionalSkip: Boolean)
    suspend fun recordSeek(playbackInstanceId: UUID, positionMs: Long, durationMs: Long)
    suspend fun clear()
    suspend fun resetPersonalization()
}

data class EventTimeContext(val localHour: Int, val dayOfWeek: DayOfWeek, val bucket: TimeBucket, val isWeekend: Boolean)

object ListeningEventSemantics {
    fun timeContext(instant: Instant, zoneId: ZoneId): EventTimeContext {
        val local = ZonedDateTime.ofInstant(instant, zoneId)
        val bucket = when (local.hour) {
            in 5..7 -> TimeBucket.EARLY_MORNING
            in 8..11 -> TimeBucket.MORNING
            in 12..16 -> TimeBucket.AFTERNOON
            in 17..20 -> TimeBucket.EVENING
            21, 22, 23, 0 -> TimeBucket.NIGHT
            else -> TimeBucket.LATE_NIGHT
        }
        return EventTimeContext(local.hour, local.dayOfWeek, bucket, local.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
    }

    fun outcome(positionMs: Long, durationMs: Long, intentionalSkip: Boolean): ListeningEventType {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val ratio = positionMs.coerceAtLeast(0L).toDouble() / safeDuration
        if (ratio >= 0.90 || safeDuration - positionMs <= 15_000L) return ListeningEventType.PLAY_COMPLETED
        if (!intentionalSkip) return ListeningEventType.PLAY_STOPPED
        return if (positionMs < 30_000L && ratio < 0.20) ListeningEventType.SKIPPED_EARLY else ListeningEventType.SKIPPED_LATE
    }
}
