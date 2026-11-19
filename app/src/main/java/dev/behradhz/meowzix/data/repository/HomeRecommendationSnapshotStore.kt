package dev.behradhz.meowzix.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.homeRecommendationDataStore by preferencesDataStore("home_recommendation_snapshot")

/** A single durable, small ordered snapshot. No Room migration or history query is required. */
data class HomeRecommendationSnapshot(
    val slotKey: String,
    val trackIds: List<UUID>,
)

internal object HomeRecommendationSnapshotCodec {
    fun encode(ids: List<UUID>): String = ids.joinToString(",")
    fun decode(encoded: String?): List<UUID>? {
        if (encoded == null) return null
        if (encoded.isEmpty()) return emptyList()
        val pieces = encoded.split(",")
        if (pieces.size > 14) return null
        val parsed = pieces.map { part -> runCatching { UUID.fromString(part) }.getOrNull() ?: return null }
        return parsed.takeIf { it.distinct().size == it.size }
    }
}

@Singleton
class HomeRecommendationSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun read(): HomeRecommendationSnapshot? {
        val preferences = context.homeRecommendationDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        val key = preferences[SLOT_KEY]?.takeIf(String::isNotBlank) ?: return null
        val ids = HomeRecommendationSnapshotCodec.decode(preferences[TRACK_IDS]) ?: return null
        return HomeRecommendationSnapshot(key, ids)
    }

    suspend fun save(snapshot: HomeRecommendationSnapshot) {
        require(snapshot.slotKey.isNotBlank())
        require(snapshot.trackIds.size <= 14 && snapshot.trackIds.distinct().size == snapshot.trackIds.size)
        context.homeRecommendationDataStore.edit {
            // The ID list and its slot marker are committed atomically.
            it[SLOT_KEY] = snapshot.slotKey
            it[TRACK_IDS] = HomeRecommendationSnapshotCodec.encode(snapshot.trackIds)
        }
    }

    private companion object {
        val SLOT_KEY = stringPreferencesKey("slot_key")
        val TRACK_IDS = stringPreferencesKey("track_ids")
    }
}
