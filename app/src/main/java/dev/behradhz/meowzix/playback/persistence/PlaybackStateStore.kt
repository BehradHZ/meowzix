package dev.behradhz.meowzix.playback.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
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
                val session = preferences[SESSION]
                    ?.let(PlaybackSessionCodec::decode)
                    ?: PersistedPlaybackSession.Empty
                val progress = preferences[PROGRESS]?.let(::decodeProgress)
                if (progress == null || session.items.isEmpty()) {
                    session
                } else {
                    val restoredIndex = progress.mediaId
                        ?.let { mediaId -> session.items.indexOfFirst { it.mediaId == mediaId } }
                        ?.takeIf { it >= 0 }
                        ?: session.currentIndex
                    session.copy(
                        currentIndex = restoredIndex.coerceIn(session.items.indices),
                        positionMs = progress.positionMs,
                    )
                }
            }
            .first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Playback restoration is a convenience. A corrupt/stale persisted snapshot must never
        // prevent the media service or the app shell from starting.
        PersistedPlaybackSession.Empty
    }

    /** Saves queue topology. This may contain thousands of lightweight logical items. */
    suspend fun save(session: PersistedPlaybackSession) {
        try {
            context.playbackDataStore.edit { preferences ->
                preferences[SESSION] = PlaybackSessionCodec.encode(session)
                val currentMediaId = session.items.getOrNull(session.currentIndex)?.mediaId
                preferences[PROGRESS] = encodeProgress(currentMediaId, session.positionMs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // Keep playback alive even if the resume snapshot cannot be persisted.
        }
    }

    /**
     * Persists only the moving playback cursor. Keeping this separate avoids rewriting a large
     * logical queue every second while music is playing.
     */
    suspend fun saveProgress(mediaId: String?, positionMs: Long) {
        try {
            context.playbackDataStore.edit { preferences ->
                preferences[PROGRESS] = encodeProgress(mediaId, positionMs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // Resume position is best-effort and must never interrupt playback.
        }
    }

    private fun encodeProgress(mediaId: String?, positionMs: Long): String {
        val encodedId = mediaId?.let { value ->
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        } ?: NULL_VALUE
        return "$encodedId|${positionMs.coerceAtLeast(0)}"
    }

    private fun decodeProgress(value: String): PersistedProgress? = runCatching {
        val fields = value.split('|')
        require(fields.size == 2)
        val mediaId = if (fields[0] == NULL_VALUE) {
            null
        } else {
            String(Base64.getUrlDecoder().decode(fields[0]), StandardCharsets.UTF_8)
        }
        PersistedProgress(mediaId, fields[1].toLong().coerceAtLeast(0))
    }.getOrNull()

    private data class PersistedProgress(
        val mediaId: String?,
        val positionMs: Long,
    )

    private companion object {
        const val NULL_VALUE = "-"
        val SESSION = stringPreferencesKey("session")
        val PROGRESS = stringPreferencesKey("progress")
    }
}
