package dev.behradhz.meowzix.data.lyrics

import androidx.room.withTransaction
import dev.behradhz.meowzix.data.db.LyricsDao
import dev.behradhz.meowzix.data.db.LyricsVersionEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsRepository
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomLyricsRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: LyricsDao,
) : LyricsRepository {
    override fun observe(trackId: UUID): Flow<TrackLyrics> = dao.observeForTrack(trackId.toString()).map { rows ->
        val versions = rows.mapNotNull(::toDomain)
        TrackLyrics(
            trackId = trackId,
            selected = versions.firstOrNull { it.selected }
                ?: versions.firstOrNull { it.userSelected }
                ?: versions.firstOrNull(),
            versions = versions,
        )
    }

    override suspend fun importLrc(trackId: UUID, rawText: String, sourceLabel: String?): UUID {
        val parsed = LrcParser.parse(rawText)
        require(parsed.contentType == LyricsContentType.TIMED || parsed.contentType == LyricsContentType.INSTRUMENTAL) {
            "The selected file does not contain usable LRC timestamps"
        }
        return saveNew(trackId, LyricsSourceType.USER_LRC, rawText, sourceLabel, userSelected = true, select = true)
    }

    override suspend fun savePlainText(trackId: UUID, text: String): UUID {
        require(text.length <= LrcParser.MAX_CHARS && text.isNotBlank()) { "Lyrics text is empty or too large" }
        return saveNew(trackId, LyricsSourceType.USER_PASTE, text, "Pasted lyrics", userSelected = true, select = true)
    }

    override suspend fun saveEmbedded(trackId: UUID, rawText: String, sourceLabel: String?): UUID {
        val parsed = LrcParser.parse(rawText)
        require(parsed.contentType != LyricsContentType.INVALID) { "Embedded lyrics are invalid" }
        val hasUserChoice = dao.observeForTrack(trackId.toString())
        // Selection protection is enforced transactionally below using the current rows.
        @Suppress("UNUSED_VARIABLE") val providerBoundary = hasUserChoice
        return saveNew(trackId, LyricsSourceType.EMBEDDED, rawText, sourceLabel, userSelected = false, select = false)
    }

    override suspend fun selectVersion(trackId: UUID, versionId: UUID) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            check(dao.select(versionId.toString(), trackId.toString(), userSelected = true, updatedAt = now)) {
                "Lyrics version not found"
            }
        }
    }

    override suspend fun setUserDelay(versionId: UUID, delayMs: Long) {
        require(delayMs in MIN_DELAY_MS..MAX_DELAY_MS) { "Lyrics delay is outside supported range" }
        check(dao.updateDelay(versionId.toString(), delayMs, System.currentTimeMillis()) == 1) {
            "Lyrics version not found"
        }
    }

    override suspend fun deleteVersion(trackId: UUID, versionId: UUID) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val selected = dao.byId(versionId.toString())?.selected == true
            dao.delete(versionId.toString(), trackId.toString())
            if (selected) {
                dao.fallbackVersionId(trackId.toString())?.let { fallback ->
                    dao.select(fallback, trackId.toString(), userSelected = false, updatedAt = now)
                }
            }
        }
    }

    private suspend fun saveNew(
        trackId: UUID,
        sourceType: LyricsSourceType,
        rawText: String,
        sourceLabel: String?,
        userSelected: Boolean,
        select: Boolean,
    ): UUID {
        val parsed = LrcParser.parse(rawText)
        require(parsed.contentType != LyricsContentType.INVALID) { "Lyrics are invalid" }
        val id = UUID.randomUUID()
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = dao.byId(id.toString())
            check(existing == null)
            if (select) dao.clearSelection(trackId.toString())
            dao.insert(
                LyricsVersionEntity(
                    id = id.toString(),
                    trackId = trackId.toString(),
                    sourceType = sourceType.name,
                    sourceLabel = sourceLabel,
                    rawText = rawText,
                    contentType = parsed.contentType.name,
                    selected = select,
                    userSelected = userSelected,
                    userDelayMs = 0L,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        return id
    }

    private fun toDomain(row: LyricsVersionEntity): LyricsVersion? = runCatching {
        val rawParsed = LrcParser.parse(row.rawText)
        LyricsVersion(
            id = UUID.fromString(row.id),
            trackId = UUID.fromString(row.trackId),
            sourceType = LyricsSourceType.valueOf(row.sourceType),
            sourceLabel = row.sourceLabel,
            rawText = row.rawText,
            parsed = rawParsed,
            selected = row.selected,
            userSelected = row.userSelected,
            userDelayMs = row.userDelayMs,
            createdAtEpochMs = row.createdAtEpochMs,
            updatedAtEpochMs = row.updatedAtEpochMs,
        )
    }.getOrNull()

    private companion object {
        const val MIN_DELAY_MS = -30_000L
        const val MAX_DELAY_MS = 30_000L
    }
}
