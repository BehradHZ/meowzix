package dev.behradhz.meowzix.data.telegram

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.telegramSourceHistoryDataStore by preferencesDataStore("telegram_source_history")

/**
 * Keeps a per-account record of chats explicitly selected at least once.
 * Deselecting a source removes its active playlist, but not this lightweight history.
 * No messages, phone numbers, proxy credentials or session secrets are stored here.
 */
@Singleton
class TelegramSourceHistoryStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun previouslySelected(accountId: String): Set<Long> =
        context.telegramSourceHistoryDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { preferences ->
                preferences[key(accountId)].orEmpty().mapNotNull(String::toLongOrNull).toSet()
            }
            .first()

    suspend fun recordSelection(accountId: String, chatId: Long) {
        context.telegramSourceHistoryDataStore.edit { preferences ->
            val key = key(accountId)
            preferences[key] = preferences[key].orEmpty() + chatId.toString()
        }
    }

    private fun key(accountId: String) = stringSetPreferencesKey("selected_once_$accountId")
}
