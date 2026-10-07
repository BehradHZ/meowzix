package dev.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.RulePlaylistEntity
import dev.behradhz.meowzix.data.db.RulePlaylistRecordRow
import dev.behradhz.meowzix.data.db.SearchTrackRow
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackMergeJournalEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.domain.library.DuplicateCandidate
import dev.behradhz.meowzix.domain.library.DuplicateEvidence
import dev.behradhz.meowzix.domain.library.LibraryToolsRepository
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistDefinition
import dev.behradhz.meowzix.domain.library.RulePlaylistRecord
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
import dev.behradhz.meowzix.domain.library.TrackMergeJournal
import dev.behradhz.meowzix.domain.library.TrackMergeResult
import dev.behradhz.meowzix.domain.library.TrackMetadataOverride
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class RoomLibraryToolsRepository @Inject constructor(
    private val toolsDao: LibraryToolsDao,
    private val libraryDao: LibraryDao,
    private val browseDao: LibraryBrowseDao,
    private val database: MeowzixDatabase,
    private val feedbackRepository: RecommendationFeedbackRepository,
    private val personalizationTrainer: PersonalizationTrainer,
    private val searchIndexer: EffectiveTrackSearchIndexer,
) : LibraryToolsRepository {
    private val clock: Clock = Clock.systemUTC()

    override fun observeMetadataOverrides(): Flow<Map<UUID, TrackMetadataOverride>> =
        toolsDao.observeMetadataOverrides().map { rows ->
            rows.associate { entity -> UUID.fromString(entity.trackId) to entity.toDomain() }
        }

    override suspend fun metadataOverride(trackId: UUID): TrackMetadataOverride? =
        toolsDao.metadataOverride(trackId.toString())?.toDomain()

    override suspend fun setMetadataOverride(value: TrackMetadataOverride?) {
        if (value == null) return
        val trackId = value.trackId.toString()
        if (value.isEmpty) {
            toolsDao.deleteMetadataOverride(trackId)
            searchIndexer.refreshTrack(trackId)
            return
        }
        toolsDao.upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = trackId,
                title = value.title.cleaned(),
                artist = value.artist.cleaned(),
                album = value.album.cleaned(),
                artworkRef = value.artworkRef.cleaned(maxLength = 4_096),
                updatedAtEpochMs = value.updatedAtEpochMs,
            ),
        )
        searchIndexer.refreshTrack(trackId)
    }

    override suspend fun duplicateCandidates(trackId: UUID): List<DuplicateCandidate> {
        val id = trackId.toString()
        val track = libraryDao.trackById(id) ?: return emptyList()
        val exactIds = libraryDao.sourcesForTrack(id)
            .mapNotNull { it.contentHashSha256 }
            .distinct()
            .flatMap { libraryDao.matchingTracksByContentHash(it) }
            .map { it.trackId }
            .filterNot { it == id }
            .toSet()

        val tolerance = max(2_000L, track.durationMs / 50L)
        val metadataIds = track.normalizedArtist?.let { artist ->
            libraryDao.matchingTrackCandidates(
                normalizedTitle = track.normalizedTitle,
                normalizedArtist = artist,
                minDurationMs = (track.durationMs - tolerance).coerceAtLeast(0L),
                maxDurationMs = track.durationMs + tolerance,
            ).map { it.id }.filterNot { it == id }.toSet()
        }.orEmpty()

        return (exactIds + metadataIds).mapNotNull { candidateId ->
            libraryDao.trackById(candidateId)?.let { entity ->
                DuplicateCandidate(
                    track = entity.toDomain(),
                    evidence = if (candidateId in exactIds) DuplicateEvidence.EXACT_CONTENT else DuplicateEvidence.METADATA_AND_DURATION,
                    sourceLabels = libraryDao.sourcesForTrack(candidateId)
                        .map { source -> "${source.type.name.lowercase()} · ${source.availability.name.lowercase()}" }
                        .distinct()
                        .sorted(),
                )
            }
        }.sortedWith(compareBy({ it.evidence != DuplicateEvidence.EXACT_CONTENT }, { it.track.normalizedTitle }))
    }

    override suspend fun mergeTracks(
        survivorTrackId: UUID,
        mergedTrackId: UUID,
        confirmMetadataOnly: Boolean,
    ): TrackMergeResult {
        require(survivorTrackId != mergedTrackId) { "A track cannot be merged into itself" }
        val survivorId = survivorTrackId.toString()
        val mergedId = mergedTrackId.toString()
        val survivor = requireNotNull(libraryDao.trackById(survivorId)) { "Survivor track does not exist" }
        val merged = requireNotNull(libraryDao.trackById(mergedId)) { "Merged track does not exist" }
        require(!survivor.hidden && !merged.hidden) { "Only visible canonical tracks can be merged" }
        val active = toolsDao.activeMergeJournal()
        require(active.none { row ->
            row.survivorTrackId in setOf(survivorId, mergedId) || row.mergedTrackId in setOf(survivorId, mergedId)
        }) { "Nested or repeated merges must be undone before merging again" }

        val evidence = duplicateCandidates(survivorTrackId).firstOrNull { it.track.id == mergedTrackId }?.evidence
            ?: duplicateCandidates(mergedTrackId).firstOrNull { it.track.id == survivorTrackId }?.evidence
            ?: error("Tracks are not a duplicate candidate pair")
        require(evidence != DuplicateEvidence.METADATA_AND_DURATION || confirmMetadataOnly) {
            "Metadata-only duplicate merges require explicit confirmation"
        }

        val playlistDao = database.playlistDao()
        val lyricsDao = database.lyricsDao()
        val mergeDao = database.trackMergeDao()
        val survivorOverride = toolsDao.metadataOverride(survivorId)
        val mergedOverride = toolsDao.metadataOverride(mergedId)
        val mergedPlaylistEntries = playlistDao.entriesForTrack(mergedId)
        val mergedFeedback = feedbackRepository.snapshot().filter { it.trackId == mergedTrackId }
        val survivorFeedback = feedbackRepository.snapshot().filter { it.trackId == survivorTrackId }
        val snapshot = TrackMergeSnapshot(
            survivorFavorite = survivor.favorite,
            survivorHidden = survivor.hidden,
            mergedFavorite = merged.favorite,
            mergedHidden = merged.hidden,
            survivorOverride = survivorOverride,
            sourceIds = libraryDao.sourcesForTrack(mergedId).map { it.id },
            playlistEntries = mergedPlaylistEntries.map { entry ->
                PlaylistSnapshotEntry(entry, playlistDao.entry(entry.playlistId, survivorId) != null)
            },
            lyricIds = lyricsDao.versionsForTrack(mergedId).map { it.id },
            historyEventIds = mergeDao.eventIdsForTrack(mergedId),
            survivorFeedback = survivorFeedback,
            mergedFeedback = mergedFeedback,
        )
        val now = clock.millis()
        val journalId = UUID.randomUUID()

        database.withTransaction {
            toolsDao.upsertMergeJournal(
                TrackMergeJournalEntity(
                    id = journalId.toString(),
                    survivorTrackId = survivorId,
                    mergedTrackId = mergedId,
                    snapshotJson = TrackMergeSnapshotCodec.encode(snapshot),
                    createdAtEpochMs = now,
                    reversedAtEpochMs = null,
                ),
            )

            snapshot.sourceIds.forEach { sourceId ->
                libraryDao.sourceById(sourceId)?.takeIf { it.trackId == mergedId }?.let {
                    libraryDao.moveSource(sourceId, survivorId)
                }
            }

            snapshot.playlistEntries.forEach { row ->
                playlistDao.removeTrack(row.entry.playlistId, mergedId)
                if (!row.survivorWasAlreadyPresent) {
                    playlistDao.insertTrack(row.entry.copy(trackId = survivorId))
                }
                playlistDao.touchPlaylist(row.entry.playlistId, now)
            }

            snapshot.lyricIds.forEach { lyricsDao.moveVersion(it, mergedId, survivorId) }
            snapshot.historyEventIds.forEach { mergeDao.moveEvent(it, mergedId, survivorId) }

            libraryDao.upsertTrack(survivor.copy(favorite = survivor.favorite || merged.favorite, updatedAtEpochMs = now))
            libraryDao.upsertTrack(merged.copy(hidden = true, updatedAtEpochMs = now))

            val combinedOverride = combineOverrides(survivorId, survivorOverride, mergedOverride, now)
            if (combinedOverride != null) toolsDao.upsertMetadataOverride(combinedOverride)

            database.audioFeatureDao().deleteForTrack(survivorId)
            database.audioFeatureDao().deleteForTrack(mergedId)
            database.historyDao().rebuildPreferenceStats(0L)
        }

        mergeFeedback(survivorTrackId, mergedTrackId, survivorFeedback, mergedFeedback)
        searchIndexer.refreshTracks(listOf(survivorId, mergedId))
        personalizationTrainer.rebuildFromStoredHistory()
        return TrackMergeResult(journalId, survivorTrackId, mergedTrackId, evidence)
    }

    override suspend fun unmerge(journalId: UUID): Boolean {
        val journal = toolsDao.mergeJournal(journalId.toString()) ?: return false
        if (journal.reversedAtEpochMs != null) return false
        val snapshot = runCatching { TrackMergeSnapshotCodec.decode(journal.snapshotJson) }.getOrElse { return false }
        val survivorId = journal.survivorTrackId
        val mergedId = journal.mergedTrackId
        val survivor = libraryDao.trackById(survivorId) ?: return false
        val merged = libraryDao.trackById(mergedId) ?: return false
        val playlistDao = database.playlistDao()
        val lyricsDao = database.lyricsDao()
        val mergeDao = database.trackMergeDao()
        val now = clock.millis()

        database.withTransaction {
            snapshot.sourceIds.forEach { sourceId ->
                libraryDao.sourceById(sourceId)?.takeIf { it.trackId == survivorId }?.let {
                    libraryDao.moveSource(sourceId, mergedId)
                }
            }

            snapshot.playlistEntries.forEach { row ->
                val currentSurvivor = playlistDao.entry(row.entry.playlistId, survivorId)
                if (!row.survivorWasAlreadyPresent && currentSurvivor?.addedAtEpochMs == row.entry.addedAtEpochMs) {
                    playlistDao.removeTrack(row.entry.playlistId, survivorId)
                }
                if (playlistDao.entry(row.entry.playlistId, mergedId) == null) {
                    val current = playlistDao.entries(row.entry.playlistId)
                    val occupied = current.map { it.position }.toSet()
                    val restoredPosition = if (row.entry.position !in occupied) row.entry.position else nextFreePosition(occupied, row.entry.position)
                    playlistDao.insertTrack(row.entry.copy(trackId = mergedId, position = restoredPosition))
                }
                playlistDao.touchPlaylist(row.entry.playlistId, now)
            }

            snapshot.lyricIds.forEach { id ->
                lyricsDao.byId(id)?.takeIf { it.trackId == survivorId }?.let { lyricsDao.moveVersion(id, survivorId, mergedId) }
            }
            snapshot.historyEventIds.forEach { mergeDao.moveEvent(it, survivorId, mergedId) }

            libraryDao.upsertTrack(
                survivor.copy(
                    favorite = snapshot.survivorFavorite,
                    hidden = snapshot.survivorHidden,
                    updatedAtEpochMs = now,
                ),
            )
            libraryDao.upsertTrack(
                merged.copy(
                    favorite = snapshot.mergedFavorite,
                    hidden = snapshot.mergedHidden,
                    updatedAtEpochMs = now,
                ),
            )

            toolsDao.deleteMetadataOverride(survivorId)
            snapshot.survivorOverride?.let { toolsDao.upsertMetadataOverride(it) }
            database.audioFeatureDao().deleteForTrack(survivorId)
            database.audioFeatureDao().deleteForTrack(mergedId)
            database.historyDao().rebuildPreferenceStats(0L)
            check(toolsDao.markMergeReversed(journal.id, now) == 1) { "Merge was already reversed" }
        }

        restoreFeedback(UUID.fromString(survivorId), snapshot.survivorFeedback)
        restoreFeedback(UUID.fromString(mergedId), snapshot.mergedFeedback)
        searchIndexer.refreshTracks(listOf(survivorId, mergedId))
        personalizationTrainer.rebuildFromStoredHistory()
        return true
    }

    override suspend fun activeMerges(): List<TrackMergeJournal> = toolsDao.activeMergeJournal().mapNotNull { row ->
        runCatching {
            TrackMergeJournal(
                id = UUID.fromString(row.id),
                survivorTrackId = UUID.fromString(row.survivorTrackId),
                mergedTrackId = UUID.fromString(row.mergedTrackId),
                createdAtEpochMs = row.createdAtEpochMs,
            )
        }.getOrNull()
    }

    private suspend fun mergeFeedback(
        survivorId: UUID,
        mergedId: UUID,
        survivorRows: List<RecommendationFeedback>,
        mergedRows: List<RecommendationFeedback>,
    ) {
        val survivorPreference = survivorRows.filter { it.action != RecommendationFeedbackAction.SNOOZE }.maxByOrNull { it.createdAt }
        val mergedPreference = mergedRows.filter { it.action != RecommendationFeedbackAction.SNOOZE }.maxByOrNull { it.createdAt }
        val preference = survivorPreference ?: mergedPreference
        val snooze = (survivorRows + mergedRows)
            .filter { it.action == RecommendationFeedbackAction.SNOOZE }
            .maxByOrNull { it.expiresAt ?: it.createdAt }
        feedbackRepository.undo(survivorId)
        feedbackRepository.undo(mergedId)
        preference?.let { putFeedback(survivorId, it) }
        snooze?.let { putFeedback(survivorId, it) }
    }

    private suspend fun restoreFeedback(trackId: UUID, rows: List<RecommendationFeedback>) {
        feedbackRepository.undo(trackId)
        rows.sortedBy { it.createdAt }.forEach { putFeedback(trackId, it) }
    }

    private suspend fun putFeedback(trackId: UUID, row: RecommendationFeedback) {
        val duration = row.expiresAt?.let { Duration.between(row.createdAt, it) }
            ?.coerceAtLeast(Duration.ofMinutes(1)) ?: RecommendationFeedback.DEFAULT_SNOOZE
        feedbackRepository.set(trackId, row.action, row.createdAt, duration)
    }

    private fun combineOverrides(
        survivorId: String,
        survivor: TrackMetadataOverrideEntity?,
        merged: TrackMetadataOverrideEntity?,
        now: Long,
    ): TrackMetadataOverrideEntity? {
        if (survivor == null && merged == null) return null
        return TrackMetadataOverrideEntity(
            trackId = survivorId,
            title = survivor?.title ?: merged?.title,
            artist = survivor?.artist ?: merged?.artist,
            album = survivor?.album ?: merged?.album,
            artworkRef = survivor?.artworkRef ?: merged?.artworkRef,
            updatedAtEpochMs = now,
        )
    }

    override fun observeRulePlaylists(): Flow<List<RulePlaylistDefinition>> =
        toolsDao.observeRulePlaylists().map { rows -> rows.mapNotNull(RulePlaylistEntity::toDomain) }

    override fun observeRulePlaylistRecords(): Flow<List<RulePlaylistRecord>> =
        toolsDao.observeRulePlaylistRecords().map { rows -> rows.mapNotNull(RulePlaylistRecordRow::toDomain) }

    override fun observeRulePlaylistTracks(playlistId: UUID): Flow<List<Track>> =
        toolsDao.observeRulePlaylists()
            .map { rows -> rows.firstOrNull { it.playlistId == playlistId.toString() }?.toDomain() }
            .distinctUntilChanged()
            .flatMapLatest { definition ->
                if (definition == null) {
                    flowOf(emptyList())
                } else {
                    ruleBoundaryRefreshes(definition).flatMapLatest { now ->
                        val query = buildRuleQuery(definition, now, RULE_LIVE_LIMIT)
                            ?: return@flatMapLatest flowOf(emptyList())
                        database.rulePlaylistQueryDao().observeTracks(query).map { rows ->
                            rows.distinctBy { it.id }.map(SearchTrackRow::toDomain)
                        }
                    }
                }
            }

    override suspend fun rulePlaylist(playlistId: UUID): RulePlaylistDefinition? =
        toolsDao.rulePlaylist(playlistId.toString())?.toDomain()

    override suspend fun saveRulePlaylist(definition: RulePlaylistDefinition) {
        require(definition.rules.isNotEmpty()) { "Rule playlist needs at least one rule" }
        require(definition.rules.size <= MAX_RULES) { "Too many rules" }
        toolsDao.upsertRulePlaylist(
            RulePlaylistEntity(
                playlistId = definition.playlistId.toString(),
                matchMode = definition.matchMode.name,
                rulesJson = RulePlaylistCodec.encode(definition.rules),
                sortMode = definition.sort.name,
                updatedAtEpochMs = definition.updatedAtEpochMs,
            ),
        )
    }

    override suspend fun deleteRulePlaylist(playlistId: UUID) {
        toolsDao.deleteRulePlaylist(playlistId.toString())
    }

    override suspend fun evaluateRulePlaylist(playlistId: UUID, limit: Int): List<Track> {
        val definition = rulePlaylist(playlistId) ?: return emptyList()
        val query = buildRuleQuery(definition, clock.millis(), limit) ?: return emptyList()
        return browseDao.searchTracks(query).distinctBy { it.id }.map(SearchTrackRow::toDomain)
    }

    private fun ruleBoundaryRefreshes(definition: RulePlaylistDefinition): Flow<Long> = flow {
        while (true) {
            val now = clock.millis()
            emit(now)
            val nextBoundary = nextRuleBoundary(definition, now) ?: awaitCancellation()
            delay((nextBoundary - clock.millis()).coerceAtLeast(MIN_BOUNDARY_DELAY_MS))
        }
    }

    private suspend fun nextRuleBoundary(definition: RulePlaylistDefinition, now: Long): Long? {
        val queryDao = database.rulePlaylistQueryDao()
        return definition.rules.mapNotNull { rule ->
            val days = rule.value.safeDays() ?: return@mapNotNull null
            val windowMs = days * DAY_MS
            when (rule.kind) {
                RuleKind.ADDED_WITHIN_DAYS -> queryDao.nextAddedExpiry(windowMs, now)
                RuleKind.NOT_LISTENED_WITHIN_DAYS -> queryDao.nextMeaningfulListenExpiry(windowMs, now)
                else -> null
            }
        }.minOrNull()
    }

    private fun buildRuleQuery(definition: RulePlaylistDefinition, now: Long, limit: Int): SimpleSQLiteQuery? {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        definition.rules.forEach { rule ->
            rule.toSqlClause(now)?.let { (sql, values) ->
                clauses += sql
                args.addAll(values)
            }
        }
        if (clauses.isEmpty()) return null
        val operator = if (definition.matchMode == RuleMatchMode.ALL) " AND " else " OR "
        val orderBy = when (definition.sort) {
            RulePlaylistSort.RECENTLY_ADDED -> "t.createdAtEpochMs DESC, t.id ASC"
            RulePlaylistSort.TITLE -> "t.normalizedTitle ASC, t.id ASC"
            RulePlaylistSort.ARTIST -> "COALESCE(t.normalizedArtist, ''), t.normalizedTitle, t.id"
            RulePlaylistSort.LAST_PLAYED -> "COALESCE((SELECT MAX(le.occurredAtEpochMs) FROM listening_events le WHERE le.trackId = t.id), 0) DESC, t.id"
        }
        args.add(limit.coerceIn(1, 1_000))
        val sql = """
            SELECT
                t.id,
                COALESCE(o.title, t.title) AS title,
                t.normalizedTitle,
                COALESCE(o.artist, t.artist) AS artist,
                t.normalizedArtist,
                COALESCE(o.album, t.album) AS album,
                t.durationMs, t.trackNumber, t.year,
                COALESCE(o.artworkRef, t.artworkRef) AS artworkRef,
                t.favorite, t.hidden, t.createdAtEpochMs, t.updatedAtEpochMs
            FROM tracks t
            LEFT JOIN track_metadata_overrides o ON o.trackId = t.id
            WHERE t.hidden = 0
              AND EXISTS (
                  SELECT 1 FROM track_sources active
                  WHERE active.trackId = t.id AND active.availability != 'MISSING'
              )
              AND (${clauses.joinToString(operator)})
            ORDER BY $orderBy
            LIMIT ?
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun PlaylistRule.toSqlClause(nowMs: Long): Pair<String, List<Any>>? = when (kind) {
        RuleKind.FAVORITE -> "t.favorite = 1" to emptyList()
        RuleKind.OFFLINE -> "EXISTS (SELECT 1 FROM track_sources local WHERE local.trackId = t.id AND local.availability = 'AVAILABLE_LOCAL')" to emptyList()
        RuleKind.ADDED_WITHIN_DAYS -> value.safeDays()?.let { days ->
            "t.createdAtEpochMs >= ?" to listOf(nowMs - days * DAY_MS)
        }
        RuleKind.NOT_LISTENED_WITHIN_DAYS -> value.safeDays()?.let { days ->
            "NOT EXISTS (SELECT 1 FROM listening_events recent WHERE recent.trackId = t.id AND recent.occurredAtEpochMs >= ? AND recent.type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_LATE'))" to listOf(nowMs - days * DAY_MS)
        }
        RuleKind.ARTIST_IS -> TextNormalizer.normalize(value)?.let { normalized ->
            "EXISTS (SELECT 1 FROM track_search_fts search_index WHERE search_index.rowid = t.rowid AND search_index.normalizedArtist = ?)" to listOf(normalized)
        }
        RuleKind.ALBUM_IS -> TextNormalizer.normalize(value)?.let { normalized ->
            "EXISTS (SELECT 1 FROM track_search_fts search_index WHERE search_index.rowid = t.rowid AND search_index.album = ?)" to listOf(normalized)
        }
    }

    private companion object {
        const val MAX_RULES = 32
        const val DAY_MS = 86_400_000L
        const val RULE_LIVE_LIMIT = 500
        const val MIN_BOUNDARY_DELAY_MS = 50L
    }
}

private fun nextFreePosition(occupied: Set<Int>, preferred: Int): Int {
    var candidate = preferred.coerceAtLeast(0)
    while (candidate in occupied) candidate++
    return candidate
}

private fun String?.cleaned(maxLength: Int = 512): String? = this?.trim()?.take(maxLength)?.takeIf(String::isNotBlank)
private fun String?.safeDays(): Long? = this?.toLongOrNull()?.coerceIn(1L, 3_650L)

private fun TrackMetadataOverrideEntity.toDomain() = TrackMetadataOverride(
    trackId = UUID.fromString(trackId),
    title = title,
    artist = artist,
    album = album,
    artworkRef = artworkRef,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun RulePlaylistEntity.toDomain(): RulePlaylistDefinition? {
    val id = runCatching { UUID.fromString(playlistId) }.getOrNull() ?: return null
    val mode = runCatching { RuleMatchMode.valueOf(matchMode) }.getOrNull() ?: return null
    val sort = runCatching { RulePlaylistSort.valueOf(sortMode) }.getOrNull() ?: return null
    val rules = RulePlaylistCodec.decode(rulesJson)
    if (rules.isEmpty()) return null
    return RulePlaylistDefinition(id, mode, rules, sort, updatedAtEpochMs)
}

private fun RulePlaylistRecordRow.toDomain(): RulePlaylistRecord? {
    val definition = RulePlaylistEntity(
        playlistId = playlistId,
        matchMode = matchMode,
        rulesJson = rulesJson,
        sortMode = sortMode,
        updatedAtEpochMs = updatedAtEpochMs,
    ).toDomain() ?: return null
    return RulePlaylistRecord(title = title, definition = definition)
}

private fun TrackEntity.toDomain() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

private fun SearchTrackRow.toDomain() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)