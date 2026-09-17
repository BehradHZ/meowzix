package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback_session")

@Singleton
class PlaybackStateStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(): PersistedPlaybackSession = context.playbackDataStore.data
        .map { preferences ->
            preferences[SESSION]?.let(PlaybackSessionCodec::decode) ?: PersistedPlaybackSession.Empty
        }
        .first()

    suspend fun save(session: PersistedPlaybackSession) {
        context.playbackDataStore.edit { preferences ->
            preferences[SESSION] = PlaybackSessionCodec.encode(session)
        }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
    }
}
