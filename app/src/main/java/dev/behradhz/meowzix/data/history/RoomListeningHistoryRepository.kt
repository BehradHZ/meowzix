package dev.behradhz.meowzix.data.history

import androidx.room.withTransaction
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.ListeningEventEntity
import dev.behradhz.meowzix.data.db.ListeningSessionEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackPreferenceStatsEntity
import dev.behradhz.meowzix.data.db.TrackTimePreferenceEntity
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.domain.history.ListeningEvent
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.history.PlaybackInitiator
import dev.behradhz.meowzix.domain.history.TrackPreferenceStats
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomListeningHistoryRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: HistoryDao,
    private val personalizationTrainer: PersonalizationTrainer,
) : ListeningHistoryRepository {
    private val mutex = Mutex()
    private val active = mutableMapOf<UUID, ActivePlayback>()
    private var currentSession: Session? = null

    override fun observeEvents(): Flow<List<ListeningEvent>> = dao.observeEvents().map { rows ->
        rows.map { row ->
            ListeningEvent(
                id = UUID.fromString(row.id),
                trackId = UUID.fromString(row.trackId),
                type = ListeningEventType.valueOf(row.type),
                occurredAt = Instant.ofEpochMilli(row.occurredAtEpochMs),
                localHour = row.localHour,
                dayOfWeek = DayOfWeek.of(row.dayOfWeek),
                timeBucket = dev.behradhz.meowzix.domain.history.TimeBucket.valueOf(row.timeBucket),
                positionMs = row.positionMs,
                durationMs = row.durationMs,
                completionRatio = row.completionRatio,
                initiatedBy = PlaybackInitiator.valueOf(row.initiatedBy),
                playbackMode = PlaybackMode.valueOf(row.playbackMode),
            )
        }
    }

    override fun observeTrackStats(): Flow<List<TrackPreferenceStats>> = dao.observeTrackStats().map { rows ->
        rows.map { row ->
            TrackPreferenceStats(
                UUID.fromString(row.trackId), row.totalStarts, row.totalCompletions,
                row.earlySkips, row.lateSkips, row.manualSelections, row.replays,
                row.lastPlayedAtEpochMs?.let(Instant::ofEpochMilli),
            )
        }
    }

    override suspend fun startPlayback(trackId: UUID, initiatedBy: PlaybackInitiator, mode: PlaybackMode): UUID = mutex.withLock {
        val playbackId = UUID.randomUUID()
        // Restored Media3 state can outlive its canonical library row (for example after a source
        // was removed while the app was closed). History has a foreign key to Track, so recording
        // such a stale queue item must be a no-op rather than a process-fatal constraint error.
        if (database.libraryDao().trackById(trackId.toString()) == null) return@withLock playbackId

        val now = Instant.now()
        val session = session(now, mode)
        val context = ActivePlayback(playbackId, trackId, session.id, initiatedBy, mode)
        database.withTransaction {
            if (initiatedBy == PlaybackInitiator.USER) insert(context, ListeningEventType.MANUAL_SELECTED, now, null, null)
            else insert(context, ListeningEventType.AUTO_SELECTED, now, null, null)
            insert(context, ListeningEventType.PLAY_STARTED, now, 0L, null)
        }
        // Only mark the playback active once both required history rows have committed. This avoids
        // retaining a phantom active record if Room rejects the transaction for any reason.
        active[playbackId] = context
        playbackId
    }

    override suspend fun finalizePlayback(
        playbackInstanceId: UUID,
        positionMs: Long,
        durationMs: Long,
        intentionalSkip: Boolean,
    ) = mutex.withLock {
        val context = active.remove(playbackInstanceId) ?: return@withLock
        val type = ListeningEventSemantics.outcome(positionMs, durationMs, intentionalSkip)
        database.withTransaction { insert(context, type, Instant.now(), positionMs, durationMs) }
        personalizationTrainer.onPlaybackFinalized(playbackInstanceId)
    }

    override suspend fun recordSeek(playbackInstanceId: UUID, positionMs: Long, durationMs: Long) = mutex.withLock {
        val context = active[playbackInstanceId] ?: return@withLock
        database.withTransaction { insert(context, ListeningEventType.SEEKED, Instant.now(), positionMs, durationMs) }
    }

    override suspend fun clear() = mutex.withLock {
        active.clear()
        currentSession = null
        database.withTransaction {
            dao.clearEvents()
            dao.clearTimeStats()
            dao.clearTrackStats()
            dao.clearSessions()
        }
        personalizationTrainer.resetAfterHistoryClear()
    }

    override suspend fun resetPersonalization() = personalizationTrainer.resetLearningKeepHistory()

    private suspend fun session(now: Instant, mode: PlaybackMode): Session {
        val existing = currentSession
        if (existing != null && now.toEpochMilli() - existing.lastActivityEpochMs <= SESSION_TIMEOUT_MS) {
            existing.lastActivityEpochMs = now.toEpochMilli()
            return existing
        }
        existing?.let {
            dao.upsertSession(ListeningSessionEntity(it.id.toString(), it.startedAtEpochMs, now.toEpochMilli(), it.initialMode.name))
        }
        return Session(UUID.randomUUID(), now.toEpochMilli(), now.toEpochMilli(), mode).also {
            currentSession = it
            dao.upsertSession(ListeningSessionEntity(it.id.toString(), it.startedAtEpochMs, null, mode.name))
        }
    }

    private suspend fun insert(
        context: ActivePlayback,
        type: ListeningEventType,
        now: Instant,
        positionMs: Long?,
        durationMs: Long?,
    ) {
        val time = ListeningEventSemantics.timeContext(now, ZoneId.systemDefault())
        val ratio = if (positionMs != null && durationMs != null && durationMs > 0L) positionMs.toDouble() / durationMs else null
        val inserted = dao.insertEvent(
            ListeningEventEntity(
                id = UUID.randomUUID().toString(),
                playbackInstanceId = context.id.toString(),
                trackId = context.trackId.toString(),
                sessionId = context.sessionId.toString(),
                type = type.name,
                occurredAtEpochMs = now.toEpochMilli(),
                localHour = time.localHour,
                dayOfWeek = time.dayOfWeek.value,
                timeBucket = time.bucket.name,
                isWeekend = time.isWeekend,
                positionMs = positionMs,
                durationMs = durationMs,
                completionRatio = ratio,
                initiatedBy = context.initiatedBy.name,
                playbackMode = context.mode.name,
            ),
        )
        if (inserted == -1L) return
        updateStats(context.trackId, time.bucket.name, type, now.toEpochMilli())
    }

    private suspend fun updateStats(trackId: UUID, bucket: String, type: ListeningEventType, now: Long) {
        val id = trackId.toString()
        val track = dao.trackStats(id) ?: TrackPreferenceStatsEntity(id, 0, 0, 0, 0, 0, 0, null)
        dao.upsertTrackStats(
            track.copy(
                totalStarts = track.totalStarts + if (type == ListeningEventType.PLAY_STARTED) 1 else 0,
                totalCompletions = track.totalCompletions + if (type == ListeningEventType.PLAY_COMPLETED) 1 else 0,
                earlySkips = track.earlySkips + if (type == ListeningEventType.SKIPPED_EARLY) 1 else 0,
                lateSkips = track.lateSkips + if (type == ListeningEventType.SKIPPED_LATE) 1 else 0,
                manualSelections = track.manualSelections + if (type == ListeningEventType.MANUAL_SELECTED) 1 else 0,
                replays = track.replays + if (type == ListeningEventType.REPLAYED) 1 else 0,
                lastPlayedAtEpochMs = if (type == ListeningEventType.PLAY_STARTED) now else track.lastPlayedAtEpochMs,
            ),
        )
        val time = dao.timeStats(id, bucket) ?: TrackTimePreferenceEntity(id, bucket, 0, 0, 0, 0, null)
        dao.upsertTimeStats(
            time.copy(
                starts = time.starts + if (type == ListeningEventType.PLAY_STARTED) 1 else 0,
                completions = time.completions + if (type == ListeningEventType.PLAY_COMPLETED) 1 else 0,
                earlySkips = time.earlySkips + if (type == ListeningEventType.SKIPPED_EARLY) 1 else 0,
                manualSelections = time.manualSelections + if (type == ListeningEventType.MANUAL_SELECTED) 1 else 0,
                lastInteractionAtEpochMs = now,
            ),
        )
    }

    private data class ActivePlayback(val id: UUID, val trackId: UUID, val sessionId: UUID, val initiatedBy: PlaybackInitiator, val mode: PlaybackMode)
    private data class Session(val id: UUID, val startedAtEpochMs: Long, var lastActivityEpochMs: Long, val initialMode: PlaybackMode)

    private companion object { const val SESSION_TIMEOUT_MS = 30 * 60 * 1_000L }
}
