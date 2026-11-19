package dev.behradhz.meowzix.data.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.data.db.LyricsDao
import dev.behradhz.meowzix.data.db.LyricsDetachedTrackEntity
import dev.behradhz.meowzix.data.db.LyricsVersionEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsRepository
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

private val Context.lyricsDisplayDataStore by preferencesDataStore("lyrics_display")
private val HIDDEN_LYRICS_TRACKS = stringSetPreferencesKey("hidden_lyrics_track_ids")

@Singleton
class RoomLyricsRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: LyricsDao,
    @param:ApplicationContext private val context: Context,
) : LyricsRepository {
    private val hiddenLyricsTrackIds = context.lyricsDisplayDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[HIDDEN_LYRICS_TRACKS].orEmpty() }

    override fun observe(trackId: UUID): Flow<TrackLyrics> = combine(
        dao.observeForTrack(trackId.toString()),
        dao.observeDiscoveryDisabled(trackId.toString()),
        hiddenLyricsTrackIds,
    ) { rows, discoveryDisabled, hiddenTracks ->
        val versions = rows.mapNotNull(::toDomain)
        TrackLyrics(
            trackId = trackId,
            selected = versions.firstOrNull { it.selected }
                ?: versions.firstOrNull { it.userSelected }
                ?: versions.firstOrNull(),
            versions = versions,
            automaticDiscoveryDisabled = discoveryDisabled,
            showInPlayer = trackId.toString() !in hiddenTracks,
        )
    }

    override suspend fun setShowInPlayer(trackId: UUID, show: Boolean) {
        context.lyricsDisplayDataStore.edit { preferences ->
            val hidden = preferences[HIDDEN_LYRICS_TRACKS].orEmpty().toMutableSet()
            if (show) hidden.remove(trackId.toString()) else hidden.add(trackId.toString())
            preferences[HIDDEN_LYRICS_TRACKS] = hidden
        }
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

    override suspend fun saveEmbedded(trackId: UUID, rawText: String, sourceLabel: String?): UUID? {
        val parsed = LrcParser.parse(rawText)
        require(parsed.contentType != LyricsContentType.INVALID) { "Embedded lyrics are invalid" }
        // A serialized check+save prevents concurrent source scans from undoing user removal.
        return database.withTransaction {
            if (dao.isDiscoveryDisabled(trackId.toString())) null
            else saveNew(trackId, LyricsSourceType.EMBEDDED, rawText, sourceLabel, userSelected = false, select = false)
        }
    }

    override suspend fun isAutomaticDiscoveryDisabled(trackId: UUID): Boolean =
        dao.isDiscoveryDisabled(trackId.toString())

    override suspend fun removeAllFromTrack(trackId: UUID) {
        database.withTransaction {
            dao.deleteAllForTrack(trackId.toString())
            dao.disableDiscovery(LyricsDetachedTrackEntity(trackId.toString()))
        }
    }

    override suspend fun restoreAutomaticDiscovery(trackId: UUID) {
        dao.enableDiscovery(trackId.toString())
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
        return database.withTransaction {
            if (sourceType == LyricsSourceType.EMBEDDED) {
                // The same source may be observed again after download/recomposition. Update in
                // place if its embedded tag changes rather than generating duplicate versions.
                val previous = dao.versionsForTrack(trackId.toString()).firstOrNull {
                    it.sourceType == LyricsSourceType.EMBEDDED.name && it.sourceLabel == sourceLabel
                }
                if (previous != null) {
                    if (previous.rawText != rawText) {
                        dao.upsert(previous.copy(
                            rawText = rawText,
                            contentType = parsed.contentType.name,
                            updatedAtEpochMs = now,
                        ))
                    }
                    return@withTransaction UUID.fromString(previous.id)
                }
            }
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
            id
        }
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
