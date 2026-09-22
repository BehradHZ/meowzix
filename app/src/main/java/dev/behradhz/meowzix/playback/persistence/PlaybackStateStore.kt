package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(): PersistedPlaybackSession = try {
        context.playbackDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { preferences ->
                preferences[SESSION]?.let(PlaybackSessionCodec::decode) ?: PersistedPlaybackSession.Empty
            }
            .first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Playback restoration is a convenience. A corrupt/stale persisted snapshot must never
        // prevent the media service or the app shell from starting.
        PersistedPlaybackSession.Empty
    }

    suspend fun save(session: PersistedPlaybackSession) {
        try {
            context.playbackDataStore.edit { preferences ->
                preferences[SESSION] = PlaybackSessionCodec.encode(session)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // Keep playback alive even if the small resume snapshot cannot be persisted.
        }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
    }
}
