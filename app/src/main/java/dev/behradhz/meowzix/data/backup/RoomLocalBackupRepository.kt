package dev.behradhz.meowzix.data.backup

import androidx.room.withTransaction
import dev.behradhz.meowzix.data.db.BackupUnresolvedReferenceEntity
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.data.db.ListeningEventEntity
import dev.behradhz.meowzix.data.db.ListeningSessionEntity
import dev.behradhz.meowzix.data.db.LyricsDao
import dev.behradhz.meowzix.data.db.LyricsVersionEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.PlaylistDao
import dev.behradhz.meowzix.data.db.PlaylistEntity
import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.RulePlaylistEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.domain.backup.BackupOptions
import dev.behradhz.meowzix.domain.backup.BackupPreview
import dev.behradhz.meowzix.domain.backup.BackupRestoreResult
import dev.behradhz.meowzix.domain.backup.LocalBackupRepository
import dev.behradhz.meowzix.domain.backup.PortableTrackRef
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import dev.behradhz.meowzix.domain.settings.LibraryGroupMode
import dev.behradhz.meowzix.domain.settings.LibrarySortMode
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.ThemePreference
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.first

@Singleton
class RoomLocalBackupRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val libraryDao: LibraryDao,
    private val playlistDao: PlaylistDao,
    private val lyricsDao: LyricsDao,
    private val toolsDao: LibraryToolsDao,
    private val historyDao: HistoryDao,
    private val feedbackRepository: RecommendationFeedbackRepository,
    private val settingsRepository: SettingsRepository,
) : LocalBackupRepository {

    override suspend fun export(options: BackupOptions): ByteArray {
        val backupId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val tracks = libraryDao.allTracks()
        val sources = libraryDao.allSources().groupBy { it.trackId }
        val refs = tracks.associate { track ->
            track.id to PortableTrackRef(
                originalTrackId = UUID.fromString(track.id),
                normalizedTitle = track.normalizedTitle,
                normalizedArtist = track.normalizedArtist,
                durationMs = track.durationMs,
                contentHashSha256 = sources[track.id].orEmpty().mapNotNull { it.contentHashSha256 }.sorted().firstOrNull(),
            )
        }
        val records = mutableListOf<BackupRecord>()
        tracks.forEach { track -> records += BackupRecord(TYPE_TRACK, refs.getValue(track.id).fields() + track.favorite.toString()) }
        playlistDao.allPlaylists().forEach { p ->
            records += BackupRecord(TYPE_PLAYLIST, listOf(p.id, p.title, p.description, p.artworkRef, p.createdAtEpochMs.toString(), p.updatedAtEpochMs.toString()))
        }
        playlistDao.allEntries().forEach { entry ->
            refs[entry.trackId]?.let { ref ->
                records += BackupRecord(TYPE_PLAYLIST_TRACK, listOf(entry.playlistId, entry.position.toString(), entry.addedAtEpochMs.toString()) + ref.fields())
            }
        }
        lyricsDao.allVersions().forEach { lyric ->
            refs[lyric.trackId]?.let { ref ->
                records += BackupRecord(
                    TYPE_LYRICS,
                    listOf(
                        lyric.id, lyric.sourceType, lyric.sourceLabel, lyric.rawText, lyric.contentType,
                        lyric.selected.toString(), lyric.userSelected.toString(), lyric.userDelayMs.toString(),
                        lyric.createdAtEpochMs.toString(), lyric.updatedAtEpochMs.toString(),
                    ) + ref.fields(),
                )
            }
        }
        toolsDao.allMetadataOverrides().forEach { override ->
            refs[override.trackId]?.let { ref ->
                records += BackupRecord(
                    TYPE_OVERRIDE,
                    listOf(override.title, override.artist, override.album, override.artworkRef, override.updatedAtEpochMs.toString()) + ref.fields(),
                )
            }
        }
        toolsDao.allRulePlaylists().forEach { rule ->
            records += BackupRecord(TYPE_RULE_PLAYLIST, listOf(rule.playlistId, rule.matchMode, rule.rulesJson, rule.sortMode, rule.updatedAtEpochMs.toString()))
        }
        feedbackRepository.snapshot().forEach { feedback ->
            refs[feedback.trackId.toString()]?.let { ref ->
                records += BackupRecord(
                    TYPE_FEEDBACK,
                    listOf(feedback.action.name, feedback.createdAt.toEpochMilli().toString(), feedback.expiresAt?.toEpochMilli()?.toString()) + ref.fields(),
                )
            }
        }
        records += settingsRecords()
        if (options.includeHistory) {
            historyDao.allEventsChronological().forEach { event ->
                refs[event.trackId]?.let { ref -> records += BackupRecord(TYPE_HISTORY, event.fields() + ref.fields()) }
            }
        }
        return BackupFormat.encode(backupId, createdAt, options.includeHistory, records)
    }

    override suspend fun preview(bytes: ByteArray): BackupPreview {
        val decoded = BackupFormat.decode(bytes)
        val unresolved = decoded.records.asSequence()
            .filter { it.type == TYPE_TRACK }
            .mapNotNull { it.refAt(0) }
            .count { resolve(it) == null }
        return BackupPreview(
            backupId = decoded.backupId,
            version = BackupFormat.VERSION,
            createdAtEpochMs = decoded.createdAtEpochMs,
            includeHistory = decoded.includeHistory,
            recordCounts = decoded.records.groupingBy { it.type }.eachCount(),
            unresolvedTrackReferences = unresolved,
        )
    }

    override suspend fun restore(bytes: ByteArray): BackupRestoreResult {
        val decoded = BackupFormat.decode(bytes)
        val trackRecords = decoded.records.filter { it.type == TYPE_TRACK }
        val resolution = mutableMapOf<String, String?>()
        trackRecords.forEach { record ->
            val ref = record.refAt(0) ?: return@forEach
            ref.originalTrackId?.toString()?.let { resolution[it] = resolve(ref) }
        }
        var restored = 0
        var unresolved = 0
        var skipped = 0

        suspend fun trackId(ref: PortableTrackRef?, ownerType: String, ownerId: String): String? {
            if (ref == null) { skipped += 1; return null }
            val key = ref.originalTrackId?.toString()
            val id = key?.let { resolution[it] } ?: resolve(ref)
            if (id != null) return id
            unresolved += 1
            toolsDao.upsertUnresolvedBackupReference(
                BackupUnresolvedReferenceEntity(
                    id = stableUnresolvedId(decoded.backupId, ownerType, ownerId, ref),
                    backupId = decoded.backupId,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    portableTrackRef = ref.persistenceString(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    resolvedTrackId = null,
                ),
            )
            return null
        }

        database.withTransaction {
            for (record in decoded.records) {
                runCatching {
                    when (record.type) {
                        TYPE_TRACK -> {
                            val ref = record.refAt(0) ?: return@runCatching
                            val id = trackId(ref, TYPE_TRACK, ref.originalTrackId?.toString().orEmpty()) ?: return@runCatching
                            libraryDao.setFavorite(id, record.fields.getOrNull(5).toBooleanSafe(), System.currentTimeMillis())
                            restored++
                        }
                        TYPE_PLAYLIST -> {
                            val f = record.fields
                            val id = f.required(0)
                            playlistDao.upsertPlaylist(
                                PlaylistEntity(id, f.required(1).take(256), f[2]?.take(2_048), f[3]?.take(4_096), f.long(4), f.long(5)),
                            )
                            restored++
                        }
                        TYPE_PLAYLIST_TRACK -> {
                            val f = record.fields
                            val playlistId = f.required(0)
                            val ref = record.refAt(3)
                            val id = trackId(ref, TYPE_PLAYLIST_TRACK, playlistId) ?: return@runCatching
                            val current = playlistDao.entries(playlistId)
                            if (current.none { it.trackId == id }) {
                                val requested = f.int(1).coerceAtLeast(0)
                                val used = current.map { it.position }.toSet()
                                val position = if (requested !in used) requested else (current.maxOfOrNull { it.position } ?: -1) + 1
                                playlistDao.insertTrack(PlaylistTrackEntity(playlistId, id, position, f.long(2)))
                            }
                            restored++
                        }
                        TYPE_LYRICS -> {
                            val f = record.fields
                            val id = trackId(record.refAt(10), TYPE_LYRICS, f.required(0)) ?: return@runCatching
                            lyricsDao.upsert(
                                LyricsVersionEntity(
                                    id = f.required(0), trackId = id, sourceType = f.required(1), sourceLabel = f[2], rawText = f.required(3),
                                    contentType = f.required(4), selected = f[5].toBooleanSafe(), userSelected = f[6].toBooleanSafe(),
                                    userDelayMs = f.long(7), createdAtEpochMs = f.long(8), updatedAtEpochMs = f.long(9),
                                ),
                            )
                            restored++
                        }
                        TYPE_OVERRIDE -> {
                            val f = record.fields
                            val id = trackId(record.refAt(5), TYPE_OVERRIDE, record.refAt(5)?.originalTrackId?.toString().orEmpty()) ?: return@runCatching
                            toolsDao.upsertMetadataOverride(TrackMetadataOverrideEntity(id, f[0], f[1], f[2], f[3], f.long(4)))
                            restored++
                        }
                        TYPE_RULE_PLAYLIST -> {
                            val f = record.fields
                            if (playlistDao.allPlaylists().any { it.id == f.required(0) }) {
                                toolsDao.upsertRulePlaylist(RulePlaylistEntity(f.required(0), f.required(1), f.required(2), f.required(3), f.long(4)))
                                restored++
                            } else skipped++
                        }
                        else -> Unit
                    }
                }.onFailure { skipped++ }
            }
        }

        restoreFeedback(decoded.records, ::trackId)
        restoreSettings(decoded.records)
        if (decoded.includeHistory) restoreHistory(decoded.records, ::trackId)
        return BackupRestoreResult(restored, unresolved, skipped)
    }

    private suspend fun restoreFeedback(
        records: List<BackupRecord>,
        resolver: suspend (PortableTrackRef?, String, String) -> String?,
    ) {
        for (record in records.filter { it.type == TYPE_FEEDBACK }) {
            val f = record.fields
            val ref = record.refAt(3)
            val id = resolver(ref, TYPE_FEEDBACK, ref?.originalTrackId?.toString().orEmpty()) ?: continue
            val action = runCatching { RecommendationFeedbackAction.valueOf(f.required(0)) }.getOrNull() ?: continue
            val created = Instant.ofEpochMilli(f.long(1))
            val expires = f[2]?.toLongOrNull()?.let(Instant::ofEpochMilli)
            val snoozeDuration = if (expires != null) {
                Duration.between(created, expires).let { duration -> if (duration.isNegative) Duration.ZERO else duration }
            } else {
                Duration.ofHours(24)
            }
            feedbackRepository.set(UUID.fromString(id), action, created, snoozeDuration = snoozeDuration)
        }
    }

    private suspend fun restoreHistory(
        records: List<BackupRecord>,
        resolver: suspend (PortableTrackRef?, String, String) -> String?,
    ) {
        val events = records.filter { it.type == TYPE_HISTORY }
        val sessions = mutableSetOf<String>()
        for (record in events) {
            val f = record.fields
            val ref = record.refAt(HISTORY_FIELD_COUNT)
            val trackId = resolver(ref, TYPE_HISTORY, f.getOrNull(0).orEmpty()) ?: continue
            val sessionId = f.required(3)
            if (sessions.add(sessionId)) {
                historyDao.upsertSession(ListeningSessionEntity(sessionId, f.long(5), null, f.required(14)))
            }
            historyDao.insertEvent(
                ListeningEventEntity(
                    id = f.required(0), playbackInstanceId = f.required(1), trackId = trackId, sessionId = sessionId,
                    type = f.required(4), occurredAtEpochMs = f.long(5), localHour = f.int(6), dayOfWeek = f.int(7),
                    timeBucket = f.required(8), isWeekend = f[9].toBooleanSafe(), positionMs = f[10]?.toLongOrNull(),
                    durationMs = f[11]?.toLongOrNull(), completionRatio = f[12]?.toDoubleOrNull(), initiatedBy = f.required(13),
                    playbackMode = f.required(14), eventSequence = 0L, outcomeKey = null,
                ),
            )
        }
        historyDao.rebuildPreferenceStats(0L)
    }

    private suspend fun settingsRecords(): List<BackupRecord> {
        val network = settingsRepository.networkPlaybackSettings.first()
        val storage = settingsRepository.storagePolicySettings.first()
        val display = settingsRepository.libraryDisplaySettings.first()
        val appearance = settingsRepository.appearanceSettings.first()
        val playback = settingsRepository.playbackPreferenceSettings.first()
        val recommendations = settingsRepository.recommendationPreferenceSettings.first()
        return listOf(
            setting("offline_mode", network.offlineMode), setting("wifi_only", network.wifiOnlyDownloads),
            setting("prefetch", network.prefetchEnabled), setting("prefetch_metered", network.prefetchOnMetered),
            setting("history", network.listeningHistoryEnabled), setting("cache_budget", storage.temporaryCacheBudgetBytes),
            setting("library_sort", display.sortMode.name), setting("library_group", display.groupMode.name),
            setting("theme", appearance.theme.name), setting("lyrics_scale", appearance.lyricsTextScalePercent),
            setting("reduce_motion", appearance.reduceMotion), setting("default_mode", playback.defaultMode.name),
            setting("default_repeat", playback.defaultRepeat.name), setting("resume", playback.resumeOnLaunch),
            setting("smart", recommendations.smartRecommendationsEnabled), setting("exploration", recommendations.explorationPercent),
            setting("audio_analysis", recommendations.audioAnalysisEnabled), setting("diagnostics", recommendations.diagnosticsVisible),
        )
    }

    private suspend fun restoreSettings(records: List<BackupRecord>) {
        for (record in records.filter { it.type == TYPE_SETTING }) {
            val key = record.fields.getOrNull(0) ?: continue
            val value = record.fields.getOrNull(1) ?: continue
            runCatching {
                when (key) {
                    "offline_mode" -> settingsRepository.setOfflineMode(value.toBoolean())
                    "wifi_only" -> settingsRepository.setWifiOnlyDownloads(value.toBoolean())
                    "prefetch" -> settingsRepository.setPrefetchEnabled(value.toBoolean())
                    "prefetch_metered" -> settingsRepository.setPrefetchOnMetered(value.toBoolean())
                    "history" -> settingsRepository.setListeningHistoryEnabled(value.toBoolean())
                    "cache_budget" -> settingsRepository.setTemporaryCacheBudgetBytes(value.toLong())
                    "library_sort" -> settingsRepository.setLibrarySortMode(LibrarySortMode.valueOf(value))
                    "library_group" -> settingsRepository.setLibraryGroupMode(LibraryGroupMode.valueOf(value))
                    "theme" -> settingsRepository.setThemePreference(ThemePreference.valueOf(value))
                    "lyrics_scale" -> settingsRepository.setLyricsTextScalePercent(value.toInt())
                    "reduce_motion" -> settingsRepository.setReduceMotion(value.toBoolean())
                    "default_mode" -> settingsRepository.setDefaultPlaybackMode(PlaybackMode.valueOf(value))
                    "default_repeat" -> settingsRepository.setDefaultRepeatMode(RepeatMode.valueOf(value))
                    "resume" -> settingsRepository.setResumeOnLaunch(value.toBoolean())
                    "smart" -> settingsRepository.setSmartRecommendationsEnabled(value.toBoolean())
                    "exploration" -> settingsRepository.setExplorationPercent(value.toInt())
                    "audio_analysis" -> settingsRepository.setAudioAnalysisEnabled(value.toBoolean())
                    "diagnostics" -> settingsRepository.setDiagnosticsVisible(value.toBoolean())
                }
            }
        }
    }

    private suspend fun resolve(ref: PortableTrackRef): String? {
        ref.originalTrackId?.toString()?.let { id -> if (libraryDao.trackById(id) != null) return id }
        ref.contentHashSha256?.let { hash ->
            libraryDao.matchingTracksByContentHash(hash).map { it.trackId }.distinct().singleOrNull()?.let { return it }
        }
        val artist = ref.normalizedArtist ?: return null
        val tolerance = max(2_000L, ref.durationMs / 50L)
        return libraryDao.matchingTrackCandidates(ref.normalizedTitle, artist, (ref.durationMs - tolerance).coerceAtLeast(0L), ref.durationMs + tolerance)
            .map { it.id }.distinct().singleOrNull()
    }

    private fun ListeningEventEntity.fields(): List<String?> = listOf(
        id, playbackInstanceId, trackId, sessionId, type, occurredAtEpochMs.toString(), localHour.toString(), dayOfWeek.toString(),
        timeBucket, isWeekend.toString(), positionMs?.toString(), durationMs?.toString(), completionRatio?.toString(), initiatedBy, playbackMode,
    )

    private fun PortableTrackRef.fields(): List<String?> = listOf(
        originalTrackId?.toString(), normalizedTitle, normalizedArtist, durationMs.toString(), contentHashSha256,
    )

    private fun BackupRecord.refAt(offset: Int): PortableTrackRef? {
        val f = fields
        if (f.size < offset + REF_FIELD_COUNT) return null
        val title = f.getOrNull(offset + 1) ?: return null
        val duration = f.getOrNull(offset + 3)?.toLongOrNull() ?: return null
        return PortableTrackRef(
            originalTrackId = f.getOrNull(offset)?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            normalizedTitle = title,
            normalizedArtist = f.getOrNull(offset + 2),
            durationMs = duration,
            contentHashSha256 = f.getOrNull(offset + 4),
        )
    }

    private fun PortableTrackRef.persistenceString(): String = fields().joinToString("\t") { it.orEmpty() }
    private fun stableUnresolvedId(backupId: String, ownerType: String, ownerId: String, ref: PortableTrackRef): String =
        UUID.nameUUIDFromBytes("$backupId|$ownerType|$ownerId|${ref.persistenceString()}".toByteArray()).toString()
    private fun setting(key: String, value: Any) = BackupRecord(TYPE_SETTING, listOf(key, value.toString()))

    private companion object {
        const val TYPE_TRACK = "TRACK"
        const val TYPE_PLAYLIST = "PLAYLIST"
        const val TYPE_PLAYLIST_TRACK = "PLAYLIST_TRACK"
        const val TYPE_LYRICS = "LYRICS"
        const val TYPE_OVERRIDE = "OVERRIDE"
        const val TYPE_RULE_PLAYLIST = "RULE_PLAYLIST"
        const val TYPE_FEEDBACK = "FEEDBACK"
        const val TYPE_SETTING = "SETTING"
        const val TYPE_HISTORY = "HISTORY"
        const val REF_FIELD_COUNT = 5
        const val HISTORY_FIELD_COUNT = 15
    }
}

private fun List<String?>.required(index: Int): String = getOrNull(index) ?: error("Missing backup field $index")
private fun List<String?>.long(index: Int): Long = required(index).toLong()
private fun List<String?>.int(index: Int): Int = required(index).toInt()
private fun String?.toBooleanSafe(): Boolean = this.equals("true", ignoreCase = true)
