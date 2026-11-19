package dev.behradhz.meowzix.data.telegram

import android.content.Context
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import org.json.JSONArray
import org.json.JSONObject

/** A partial preview must never be mistaken for a fully indexed Telegram folder. */
internal data class ForwardFolderChatSnapshot(
    val chats: List<TelegramChatSummary>,
    val complete: Boolean,
    val savedAtEpochMs: Long,
)

/** Private account-scoped Forward picker snapshots (excluded from Android backup). */
internal class ForwardDestinationSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("telegram_forward_destinations", Context.MODE_PRIVATE)
    private val hotFolderSnapshots = mutableMapOf<String, ForwardFolderChatSnapshot>()

    fun folders(account: String): List<TelegramForwardFolder>? = read(account, "folders") {
        TelegramForwardFolder(it.getInt("id"), it.getString("title"), emptyList())
    }
    fun saveFolders(account: String, folders: List<TelegramForwardFolder>) =
        write(account, "folders", folders) { JSONObject().put("id", it.id).put("title", it.title) }
    fun recentChats(account: String): List<TelegramChatSummary>? = readChats(account, "recent")
    fun saveRecentChats(account: String, chats: List<TelegramChatSummary>) = writeChats(account, "recent", chats)

    /**
     * Only v3 snapshots carry an end-of-list marker and an absolute timestamp.
     * Legacy v2 previews have neither and are not trusted as completed indexes.
     */
    @Synchronized
    fun folderChatSnapshot(
        account: String,
        id: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ForwardFolderChatSnapshot? {
        val key = "$account|v3_folder_$id"
        val snapshot = hotFolderSnapshots[key] ?: run {
            val raw = prefs.getString(key, null) ?: return null
            val decoded = runCatching {
                val payload = JSONObject(raw)
                val rows = payload.getJSONArray("chats")
                ForwardFolderChatSnapshot(
                    chats = (0 until rows.length()).map { decodeChat(rows.getJSONObject(it)) },
                    complete = payload.getBoolean("complete"),
                    savedAtEpochMs = payload.getLong("savedAt"),
                )
            }.getOrNull()
            if (decoded != null) hotFolderSnapshots[key] = decoded
            decoded
        }
        if (snapshot == null || !isForwardFolderIndexFresh(snapshot.savedAtEpochMs, nowEpochMs)) {
            hotFolderSnapshots.remove(key)
            prefs.edit().remove(key).remove("$account|v2_folder_$id").apply()
            return null
        }
        return snapshot
    }

    fun folderChats(account: String, id: Int): List<TelegramChatSummary>? =
        folderChatSnapshot(account, id)?.chats

    fun completeFolderChats(account: String, id: Int): List<TelegramChatSummary>? =
        folderChatSnapshot(account, id)?.takeIf { it.complete }?.chats

    /** Previews are deliberately non-final; never downgrade a valid completed index. */
    @Synchronized
    fun saveFolderChats(
        account: String,
        id: Int,
        chats: List<TelegramChatSummary>,
        savedAtEpochMs: Long = System.currentTimeMillis(),
    ) {
        if (folderChatSnapshot(account, id, savedAtEpochMs)?.complete == true) return
        saveFolderSnapshot(account, id, ForwardFolderChatSnapshot(chats, false, savedAtEpochMs))
    }

    /** Only save when TDLib confirms end-of-list and every known chat was resolved. */
    @Synchronized
    fun saveCompleteFolderChats(
        account: String,
        id: Int,
        chats: List<TelegramChatSummary>,
        savedAtEpochMs: Long = System.currentTimeMillis(),
    ) = saveFolderSnapshot(account, id, ForwardFolderChatSnapshot(chats, true, savedAtEpochMs))

    private fun saveFolderSnapshot(account: String, id: Int, snapshot: ForwardFolderChatSnapshot) {
        val key = "$account|v3_folder_$id"
        val rows = JSONArray()
        snapshot.chats.forEach { rows.put(encodeChat(it)) }
        val payload = JSONObject()
            .put("savedAt", snapshot.savedAtEpochMs)
            .put("complete", snapshot.complete)
            .put("chats", rows)
        prefs.edit().putString(key, payload.toString()).remove("$account|v2_folder_$id").apply()
        hotFolderSnapshots[key] = snapshot
    }

    @Synchronized
    fun clear(account: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$account|") }.forEach { editor.remove(it) }
        hotFolderSnapshots.keys.removeAll { it.startsWith("$account|") }
        editor.apply()
    }

    @Synchronized
    fun clearAll() {
        hotFolderSnapshots.clear()
        prefs.edit().clear().apply()
    }

    // v2 recent-chat snapshots exclude MTProxy-sponsored chats.
    private fun readChats(account: String, key: String): List<TelegramChatSummary>? =
        read(account, "v2_$key", ::decodeChat)

    private fun writeChats(account: String, key: String, chats: List<TelegramChatSummary>) =
        write(account, "v2_$key", chats, ::encodeChat)

    private fun decodeChat(value: JSONObject): TelegramChatSummary = TelegramChatSummary(
        value.getLong("id"), value.getString("title"),
        TelegramChatKind.valueOf(value.getString("kind")), false,
    )

    private fun encodeChat(chat: TelegramChatSummary): JSONObject = JSONObject()
        .put("id", chat.chatId)
        .put("title", chat.title)
        .put("kind", chat.kind.name)

    private fun <T> read(account: String, key: String, decode: (JSONObject) -> T): List<T>? {
        val raw = prefs.getString("$account|$key", null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { decode(array.getJSONObject(it)) }
        }.getOrNull()
    }

    private fun <T> write(account: String, key: String, items: List<T>, encode: (T) -> JSONObject) {
        val json = JSONArray()
        items.forEach { json.put(encode(it)) }
        prefs.edit().putString("$account|$key", json.toString()).apply()
    }
}
