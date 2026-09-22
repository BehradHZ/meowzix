package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(): PersistedPlaybackSession = try {
        val preferences = context.playbackDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
        val session = preferences[SESSION]
            ?.let(PlaybackSessionCodec::decode)
            ?: PersistedPlaybackSession.Empty
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
        PersistedPlaybackSession.Empty
    }

    /** Saves queue topology and policies. Call only when the queue/current item materially changes. */
    suspend fun save(session: PersistedPlaybackSession) {
        try {
            context.playbackDataStore.edit { preferences ->
                preferences[SESSION] = PlaybackSessionCodec.encode(session)
                session.items.getOrNull(session.currentIndex)?.mediaId?.let { mediaId ->
                    preferences[POSITION_MEDIA_ID] = mediaId
                } ?: preferences.remove(POSITION_MEDIA_ID)
                preferences[POSITION_MS] = session.positionMs.coerceAtLeast(0)
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

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val POSITION_MEDIA_ID = stringPreferencesKey("position_media_id")
        val POSITION_MS = longPreferencesKey("position_ms")
    }
}
