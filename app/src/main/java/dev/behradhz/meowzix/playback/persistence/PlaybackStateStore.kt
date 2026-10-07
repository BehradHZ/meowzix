package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.QueueProvenanceRestoreHints
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val progressiveQueue: ProgressiveQueue,
    private val toolsDao: LibraryToolsDao,
) {
    suspend fun load(): PersistedPlaybackSession = try {
        val preferences = context.playbackDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
        val decoded = preferences[SESSION]
            ?.let(PlaybackSessionCodec::decode)
            ?: PersistedPlaybackSession.Empty
        val aliases = toolsDao.activeMergeJournal().associate { it.mergedTrackId to it.survivorTrackId }
        val session = remapPersistedPlaybackSession(decoded, aliases)
        publishRestoreHints(session)
        val savedMediaId = preferences[POSITION_MEDIA_ID]
        val savedPositionMs = preferences[POSITION_MS]
        val activeMediaId = session.items.getOrNull(session.currentIndex)?.mediaId
        if (savedMediaId != null && savedMediaId == activeMediaId && savedPositionMs != null) {
            session.copy(positionMs = savedPositionMs.coerceAtLeast(0))
        } else {
            session
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        QueueProvenanceRestoreHints.clear()
        PersistedPlaybackSession.Empty
    }

    /** Saves queue topology and policies. Call only when the queue/current item materially changes. */
    suspend fun save(session: PersistedPlaybackSession) {
        try {
            val aliases = toolsDao.activeMergeJournal().associate { it.mergedTrackId to it.survivorTrackId }
            val canonicalSession = remapPersistedPlaybackSession(session, aliases)
            val logical = progressiveQueue.snapshot()
            val enriched = if (
                logical != null &&
                canonicalSession.logicalMediaIds == logical.tracks.map { track ->
                    aliases[track.id.toString()] ?: track.id.toString()
                }
            ) {
                canonicalSession.copy(logicalOrigins = logical.origins)
            } else {
                canonicalSession
            }
            context.playbackDataStore.edit { preferences ->
                preferences[SESSION] = PlaybackSessionCodec.encode(enriched)
                enriched.items.getOrNull(enriched.currentIndex)?.mediaId?.let { mediaId ->
                    preferences[POSITION_MEDIA_ID] = mediaId
                } ?: preferences.remove(POSITION_MEDIA_ID)
                preferences[POSITION_MS] = enriched.positionMs.coerceAtLeast(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
        }
    }

    /** Saves only the hot playback cursor so large logical queues are not re-serialized every second. */
    suspend fun savePosition(mediaId: String?, positionMs: Long) {
        try {
            context.playbackDataStore.edit { preferences ->
                if (mediaId == null) {
                    preferences.remove(POSITION_MEDIA_ID)
                } else {
                    preferences[POSITION_MEDIA_ID] = mediaId
                }
                preferences[POSITION_MS] = positionMs.coerceAtLeast(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
        }
    }

    private fun publishRestoreHints(session: PersistedPlaybackSession) {
        val ids = session.logicalMediaIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (ids.size == session.logicalMediaIds.size && ids.size == session.logicalOrigins.size) {
            QueueProvenanceRestoreHints.publish(ids, session.logicalOrigins)
        } else {
            QueueProvenanceRestoreHints.clear()
        }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val POSITION_MEDIA_ID = stringPreferencesKey("position_media_id")
        val POSITION_MS = longPreferencesKey("position_ms")
    }
}


internal fun remapPersistedPlaybackSession(
    session: PersistedPlaybackSession,
    aliases: Map<String, String>,
): PersistedPlaybackSession {
    if (aliases.isEmpty()) return session

    fun canonical(id: String): String = aliases[id] ?: id
    val items = session.items.map { item ->
        val mapped = canonical(item.mediaId)
        if (mapped == item.mediaId) {
            item
        } else {
            item.copy(
                mediaId = mapped,
                uri = item.uri.replace("trackId=${item.mediaId}", "trackId=$mapped"),
            )
        }
    }
    return session.copy(
        items = items,
        logicalMediaIds = session.logicalMediaIds.map(::canonical),
    )
}
