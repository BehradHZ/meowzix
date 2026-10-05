package dev.behradhz.meowzix.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.data.db.RulePlaylistEntity
import dev.behradhz.meowzix.data.db.SearchTrackRow
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.domain.library.DuplicateCandidate
import dev.behradhz.meowzix.domain.library.DuplicateEvidence
import dev.behradhz.meowzix.domain.library.LibraryToolsRepository
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistDefinition
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
import dev.behradhz.meowzix.domain.library.TrackMetadataOverride
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomLibraryToolsRepository @Inject constructor(
    private val toolsDao: LibraryToolsDao,
    private val libraryDao: LibraryDao,
    private val browseDao: LibraryBrowseDao,
) : LibraryToolsRepository {
    private val clock: Clock = Clock.systemUTC()

    override fun observeMetadataOverrides(): Flow<Map<UUID, TrackMetadataOverride>> =
        toolsDao.observeMetadataOverrides().map { rows ->
            rows.associate { entity -> UUID.fromString(entity.trackId) to entity.toDomain() }
        }

    override suspend fun metadataOverride(trackId: UUID): TrackMetadataOverride? =
        toolsDao.metadataOverride(trackId.toString())?.toDomain()

    override suspend fun setMetadataOverride(value: TrackMetadataOverride?) {
        if (value == null || value.isEmpty) {
            value?.let { toolsDao.deleteMetadataOverride(it.trackId.toString()) }
            return
        }
        toolsDao.upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = value.trackId.toString(),
                title = value.title.cleaned(),
                artist = value.artist.cleaned(),
                album = value.album.cleaned(),
                artworkRef = value.artworkRef.cleaned(maxLength = 4_096),
                updatedAtEpochMs = value.updatedAtEpochMs,
            ),
        )
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
                )
            }
        }.sortedWith(compareBy({ it.evidence != DuplicateEvidence.EXACT_CONTENT }, { it.track.normalizedTitle }))
    }

    override fun observeRulePlaylists(): Flow<List<RulePlaylistDefinition>> =
        toolsDao.observeRulePlaylists().map { rows -> rows.mapNotNull(RulePlaylistEntity::toDomain) }

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
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        definition.rules.forEach { rule ->
            rule.toSqlClause(clock.millis())?.let { (sql, values) ->
                clauses += sql
                args.addAll(values)
            }
        }
        if (clauses.isEmpty()) return emptyList()
        val operator = if (definition.matchMode == RuleMatchMode.ALL) " AND " else " OR "
        val orderBy = when (definition.sort) {
            RulePlaylistSort.RECENTLY_ADDED -> "t.createdAtEpochMs DESC, t.id ASC"
            RulePlaylistSort.TITLE -> "t.normalizedTitle ASC, t.id ASC"
            RulePlaylistSort.ARTIST -> "COALESCE(t.normalizedArtist, ''), t.normalizedTitle, t.id"
            RulePlaylistSort.LAST_PLAYED -> "COALESCE((SELECT MAX(le.occurredAtEpochMs) FROM listening_events le WHERE le.trackId = t.id), 0) DESC, t.id"
        }
        args += limit.coerceIn(1, 1_000)
        val sql = """
            SELECT
                t.id, t.title, t.normalizedTitle, t.artist, t.normalizedArtist, t.album,
                t.durationMs, t.trackNumber, t.year, t.artworkRef, t.favorite, t.hidden,
                t.createdAtEpochMs, t.updatedAtEpochMs
            FROM tracks t
            WHERE t.hidden = 0
              AND EXISTS (
                  SELECT 1 FROM track_sources active
                  WHERE active.trackId = t.id AND active.availability != 'MISSING'
              )
              AND (${clauses.joinToString(operator)})
            ORDER BY $orderBy
            LIMIT ?
        """.trimIndent()
        return browseDao.searchTracks(SimpleSQLiteQuery(sql, args.toTypedArray())).map(SearchTrackRow::toDomain)
    }

    private fun PlaylistRule.toSqlClause(nowMs: Long): Pair<String, List<Any>>? = when (kind) {
        RuleKind.FAVORITE -> "t.favorite = 1" to emptyList()
        RuleKind.OFFLINE -> "EXISTS (SELECT 1 FROM track_sources local WHERE local.trackId = t.id AND local.availability = 'AVAILABLE_LOCAL')" to emptyList()
        RuleKind.ADDED_WITHIN_DAYS -> value.safeDays()?.let { days ->
            "t.createdAtEpochMs >= ?" to listOf(nowMs - days * DAY_MS)
        }
        RuleKind.NOT_LISTENED_WITHIN_DAYS -> value.safeDays()?.let { days ->
            "NOT EXISTS (SELECT 1 FROM listening_events recent WHERE recent.trackId = t.id AND recent.occurredAtEpochMs >= ?)" to listOf(nowMs - days * DAY_MS)
        }
        RuleKind.ARTIST_IS -> TextNormalizer.normalize(value)?.let { normalized ->
            "COALESCE(t.normalizedArtist, '') = ?" to listOf(normalized)
        }
        RuleKind.ALBUM_IS -> TextNormalizer.normalize(value)?.let { normalized ->
            "LOWER(TRIM(COALESCE(t.album, ''))) = ?" to listOf(normalized)
        }
    }

    private companion object {
        const val MAX_RULES = 32
        const val DAY_MS = 86_400_000L
    }
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
