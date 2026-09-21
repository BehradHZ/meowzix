package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(): PersistedPlaybackSession {
        val preferences = context.playbackDataStore.data.first()
        val saved = preferences[SESSION]?.let(PlaybackSessionCodec::decode)
            ?: PersistedPlaybackSession.Empty
        if (saved.items.isEmpty()) return saved
        val requestedIndex = preferences[CURRENT_INDEX] ?: saved.currentIndex
        return saved.copy(
            currentIndex = requestedIndex.coerceIn(saved.items.indices),
            positionMs = (preferences[POSITION_MS] ?: saved.positionMs).coerceAtLeast(0L),
        )
    }

    suspend fun saveSession(session: PersistedPlaybackSession) {
        context.playbackDataStore.edit { preferences ->
            preferences[SESSION] = PlaybackSessionCodec.encode(session)
            preferences[CURRENT_INDEX] = session.currentIndex
            preferences[POSITION_MS] = session.positionMs
        }
    }

    suspend fun savePosition(currentIndex: Int, positionMs: Long) {
        context.playbackDataStore.edit { preferences ->
            preferences[CURRENT_INDEX] = currentIndex.coerceAtLeast(0)
            preferences[POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val POSITION_MS = longPreferencesKey("position_ms")
    }
}
