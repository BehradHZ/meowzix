package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/** The visible materialized Media3 queue, with stable positions for guarded row selection. */
data class WidgetQueueTrack(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val occurrenceId: String? = null,
    val origin: String = "GENERATED",
)

/**
 * Bound the home-screen collection to a manageable portion of the materialized
 * playback timeline; never serialize the whole 10,000-track library into RemoteViews.
 */
internal fun widgetQueueRange(mediaItemCount: Int, currentIndex: Int, maxItems: Int = 72): IntRange {
    if (mediaItemCount <= 0 || maxItems <= 0) return IntRange.EMPTY
    val start = (currentIndex.coerceAtLeast(0) - 3).coerceAtLeast(0)
        .coerceAtMost(mediaItemCount - 1)
    return start until (start + maxItems).coerceAtMost(mediaItemCount)
}

internal fun Player.widgetQueue(maxItems: Int = 72): List<WidgetQueueTrack> {
    if (!isCommandAvailable(Player.COMMAND_GET_TIMELINE) || mediaItemCount <= 0) return emptyList()
    return widgetQueueRange(mediaItemCount, currentMediaItemIndex, maxItems).map { index ->
        val item = getMediaItemAt(index)
        WidgetQueueTrack(
            index,
            item.mediaId,
            item.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: "Unknown track",
            item.mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank),
        )
    }
}

/**
 * A snapshot is used only as a no-flicker fallback while connecting to the existing
 * MediaSession. All commands always query the live session, never this saved queue.
 */
internal object PlaybackWidgetSnapshotStore {
    private const val PREFS = "meowzix_playback_widget"
    private const val KEY_STATE = "snapshot"
    private const val KEY_LAST_PLAYED = "last_played_track"
    private const val KEY_MENU = "menu_"

    fun save(context: Context, state: PlaybackWidgetState) {
        val data = JSONObject().apply {
            put("title", state.title)
            put("artist", state.artist)
            put("artwork", state.artworkRef)
            put("mediaId", state.mediaId)
            put("playing", state.isPlaying)
            put("favorite", state.isFavorite)
            put("position", state.queueIndex)
            put("total", state.queueTotal)
            put("queue", JSONArray().apply {
                state.queue.forEach { item ->
                    put(JSONObject().apply {
                        put("index", item.index)
                        put("mediaId", item.mediaId)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("occurrenceId", item.occurrenceId)
                        put("origin", item.origin)
                    })
                }
            })
        }
        preferences(context).edit().putString(KEY_STATE, data.toString()).apply()
    }

    fun load(context: Context, assumePaused: Boolean = true): PlaybackWidgetState = runCatching {
        val data = JSONObject(preferences(context).getString(KEY_STATE, null) ?: return PlaybackWidgetState.Empty)
        val queue = data.optJSONArray("queue") ?: JSONArray()
        PlaybackWidgetState(
            title = data.optString("title").takeIf { it != "null" && it.isNotBlank() },
            artist = data.optString("artist").takeIf { it != "null" && it.isNotBlank() },
            artworkRef = data.optString("artwork").takeIf { it != "null" && it.isNotBlank() },
            // A saved state cannot prove that playback is still active after process death.
            isPlaying = !assumePaused && data.optBoolean("playing"),
            isFavorite = data.optBoolean("favorite"),
            mediaId = data.optString("mediaId").takeIf { it != "null" && it.isNotBlank() },
            queueIndex = data.optInt("position", -1),
            queueTotal = data.optInt("total", 0),
            queue = (0 until queue.length()).mapNotNull { idx ->
                val item = queue.optJSONObject(idx) ?: return@mapNotNull null
                val id = item.optString("mediaId").takeIf(String::isNotBlank) ?: return@mapNotNull null
                WidgetQueueTrack(
                    item.optInt("index", -1),
                    id,
                    item.optString("title").ifBlank { "Unknown track" },
                    item.optString("artist").takeIf { it != "null" && it.isNotBlank() },
                    item.optString("occurrenceId").takeIf { it != "null" && it.isNotBlank() },
                    item.optString("origin").ifBlank { "GENERATED" },
                )
            },
        )
    }.getOrDefault(PlaybackWidgetState.Empty)

    /**
     * The last genuinely played song is available for previews even before the
     * user adds their first widget. Store only metadata, not an entire queue.
     */
    fun saveLastPlayed(context: Context, state: PlaybackWidgetState) {
        if (!state.hasTrack || !state.isPlaying) return
        val payload = JSONObject().apply {
            put("title", state.title)
            put("artist", state.artist)
            put("artwork", state.artworkRef)
            put("mediaId", state.mediaId)
        }.toString()
        val prefs = preferences(context)
        if (prefs.getString(KEY_LAST_PLAYED, null) != payload) {
            prefs.edit().putString(KEY_LAST_PLAYED, payload).apply()
        }
    }

    fun loadLastPlayed(context: Context): PlaybackWidgetState {
        val recent = runCatching {
            val stored = preferences(context).getString(KEY_LAST_PLAYED, null)
                ?: return@runCatching null
            val data = JSONObject(stored)
            PlaybackWidgetState(
                title = data.optString("title").takeIf { it.isNotBlank() && it != "null" },
                artist = data.optString("artist").takeIf { it.isNotBlank() && it != "null" },
                artworkRef = data.optString("artwork").takeIf { it.isNotBlank() && it != "null" },
                isPlaying = false,
                mediaId = data.optString("mediaId").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrNull()
        return recent?.takeIf { it.hasTrack } ?: load(context)
    }

    fun menuExpanded(context: Context, widgetId: Int): Boolean =
        preferences(context).getBoolean("$KEY_MENU$widgetId", false)

    fun toggleMenu(context: Context, widgetId: Int) {
        val prefs = preferences(context)
        val key = "$KEY_MENU$widgetId"
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply()
    }

    fun clearMenu(context: Context, ids: IntArray) {
        val edit = preferences(context).edit()
        ids.forEach { edit.remove("$KEY_MENU$it") }
        edit.apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
