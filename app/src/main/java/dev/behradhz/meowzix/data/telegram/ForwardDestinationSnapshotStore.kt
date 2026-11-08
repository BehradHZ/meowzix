package dev.behradhz.meowzix.data.telegram

import android.content.Context
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import org.json.JSONArray
import org.json.JSONObject

/** Private account-scoped Forward picker snapshots (excluded from Android backup). */
internal class ForwardDestinationSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("telegram_forward_destinations", Context.MODE_PRIVATE)
    fun folders(account: String): List<TelegramForwardFolder>? = read(account, "folders") {
        TelegramForwardFolder(it.getInt("id"), it.getString("title"), emptyList())
    }
    fun saveFolders(account: String, folders: List<TelegramForwardFolder>) =
        write(account, "folders", folders) { JSONObject().put("id", it.id).put("title", it.title) }
    fun recentChats(account: String): List<TelegramChatSummary>? = readChats(account, "recent")
    fun saveRecentChats(account: String, chats: List<TelegramChatSummary>) = writeChats(account, "recent", chats)
    fun folderChats(account: String, id: Int): List<TelegramChatSummary>? = readChats(account, "folder_$id")
    fun saveFolderChats(account: String, id: Int, chats: List<TelegramChatSummary>) = writeChats(account, "folder_$id", chats)
    fun clear(account: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$account|") }.forEach { editor.remove(it) }
        editor.apply()
    }
    fun clearAll() = prefs.edit().clear().apply()
    private fun readChats(account: String, key: String): List<TelegramChatSummary>? = read(account,key) {
        TelegramChatSummary(it.getLong("id"), it.getString("title"), TelegramChatKind.valueOf(it.getString("kind")), false)
    }
    private fun writeChats(account: String, key: String, chats: List<TelegramChatSummary>) =
        write(account,key,chats) { JSONObject().put("id",it.chatId).put("title",it.title).put("kind",it.kind.name) }
    private fun <T> read(account: String,key: String, decode: (JSONObject)->T): List<T>? {
        val raw = prefs.getString("$account|$key",null) ?: return null
        return runCatching { val array=JSONArray(raw); (0 until array.length()).map { decode(array.getJSONObject(it)) } }.getOrNull()
    }
    private fun <T> write(account: String,key: String, items: List<T>, encode: (T)->JSONObject) {
        val json=JSONArray()
        items.forEach { json.put(encode(it)) }
        prefs.edit().putString("$account|$key",json.toString()).apply()
    }
}
