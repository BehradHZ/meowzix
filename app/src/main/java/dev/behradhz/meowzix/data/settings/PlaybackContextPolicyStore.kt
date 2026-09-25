package dev.behradhz.meowzix.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackContextPolicy(
    val playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

object PlaybackContextKeys {
    const val LIBRARY = "library"

    fun playlist(playlistId: UUID): String = "playlist:$playlistId"
}

@Singleton
class PlaybackContextPolicyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun activate(contextKey: String) {
        preferences.edit().putString(KEY_ACTIVE_CONTEXT, contextKey).apply()
    }

    fun activeContextKey(): String? = preferences.getString(KEY_ACTIVE_CONTEXT, null)

    fun policy(contextKey: String): PlaybackContextPolicy = PlaybackContextPolicy(
        playbackMode = preferences.getString(modeKey(contextKey), null)
            ?.let { stored -> runCatching { PlaybackMode.valueOf(stored) }.getOrNull() }
            ?: PlaybackMode.ORDERED,
        repeatMode = preferences.getString(repeatKey(contextKey), null)
            ?.let { stored -> runCatching { RepeatMode.valueOf(stored) }.getOrNull() }
            ?: RepeatMode.OFF,
    )

    fun savePlaybackMode(contextKey: String, mode: PlaybackMode) {
        preferences.edit().putString(modeKey(contextKey), mode.name).apply()
    }

    fun saveRepeatMode(contextKey: String, mode: RepeatMode) {
        preferences.edit().putString(repeatKey(contextKey), mode.name).apply()
    }

    fun savePlaybackModeForActiveContext(mode: PlaybackMode) {
        activeContextKey()?.let { savePlaybackMode(it, mode) }
    }

    fun saveRepeatModeForActiveContext(mode: RepeatMode) {
        activeContextKey()?.let { saveRepeatMode(it, mode) }
    }

    private fun modeKey(contextKey: String) = "context.$contextKey.playback_mode"

    private fun repeatKey(contextKey: String) = "context.$contextKey.repeat_mode"

    private companion object {
        const val PREFERENCES_NAME = "playback_context_policy"
        const val KEY_ACTIVE_CONTEXT = "active_context"
    }
}
