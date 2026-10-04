package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationBus
import dev.behradhz.meowzix.domain.playback.SmartQueueAdaptationReason
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackScope
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.recommendationFeedbackDataStore by preferencesDataStore("recommendation_feedback")

@Singleton
class DataStoreRecommendationFeedbackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val adaptationBus: SmartQueueAdaptationBus,
) : RecommendationFeedbackRepository {
    private val safeData = context.recommendationFeedbackDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val feedback: Flow<List<RecommendationFeedback>> = safeData.map { preferences ->
        preferences[ENTRIES].orEmpty().mapNotNull(::decode).sortedByDescending { it.createdAt }
    }

    override suspend fun snapshot(now: Instant): List<RecommendationFeedback> =
        feedback.first().filter { it.isActive(now) }

    override suspend fun set(
        trackId: UUID,
        action: RecommendationFeedbackAction,
        now: Instant,
        snoozeDuration: Duration,
    ) {
        val replacement = RecommendationFeedback(
            trackId = trackId,
            action = action,
            createdAt = now,
            expiresAt = if (action == RecommendationFeedbackAction.SNOOZE) now.plus(snoozeDuration.coerceAtLeast(Duration.ofMinutes(1))) else null,
        )
        context.recommendationFeedbackDataStore.edit { preferences ->
            val existing = preferences[ENTRIES].orEmpty().mapNotNull(::decode).toMutableList()
            // Repeated taps are idempotent. Positive/negative explicit preference is mutually
            // exclusive, while a temporary snooze may coexist with either preference decision.
            existing.removeAll { row ->
                row.trackId == trackId && (
                    row.action == action ||
                        action != RecommendationFeedbackAction.SNOOZE &&
                        row.action != RecommendationFeedbackAction.SNOOZE
                    )
            }
            existing += replacement
            preferences[ENTRIES] = existing.map(::encode).toSet()
        }
        adaptationBus.emit(SmartQueueAdaptationReason.EXPLICIT_FEEDBACK)
    }

    override suspend fun undo(trackId: UUID, action: RecommendationFeedbackAction?) {
        context.recommendationFeedbackDataStore.edit { preferences ->
            val retained = preferences[ENTRIES].orEmpty().mapNotNull(::decode).filterNot { row ->
                row.trackId == trackId && (action == null || row.action == action)
            }
            preferences[ENTRIES] = retained.map(::encode).toSet()
        }
        adaptationBus.emit(SmartQueueAdaptationReason.EXPLICIT_FEEDBACK)
    }

    override suspend fun clearExpired(now: Instant) {
        context.recommendationFeedbackDataStore.edit { preferences ->
            val retained = preferences[ENTRIES].orEmpty().mapNotNull(::decode).filter { it.isActive(now) }
            preferences[ENTRIES] = retained.map(::encode).toSet()
        }
    }

    override suspend fun clearAll() {
        context.recommendationFeedbackDataStore.edit { it.remove(ENTRIES) }
        adaptationBus.emit(SmartQueueAdaptationReason.EXPLICIT_FEEDBACK)
    }

    private fun encode(value: RecommendationFeedback): String = listOf(
        value.trackId.toString(),
        value.action.name,
        value.createdAt.toEpochMilli().toString(),
        value.expiresAt?.toEpochMilli()?.toString() ?: NO_EXPIRY,
        value.scope.name,
        value.version.toString(),
        value.provenance,
    ).joinToString(SEPARATOR)

    private fun decode(value: String): RecommendationFeedback? = runCatching {
        val fields = value.split(SEPARATOR)
        require(fields.size == FIELD_COUNT)
        RecommendationFeedback(
            trackId = UUID.fromString(fields[0]),
            action = RecommendationFeedbackAction.valueOf(fields[1]),
            createdAt = Instant.ofEpochMilli(fields[2].toLong()),
            expiresAt = fields[3].takeUnless { it == NO_EXPIRY }?.toLong()?.let(Instant::ofEpochMilli),
            scope = RecommendationFeedbackScope.valueOf(fields[4]),
            version = fields[5].toInt(),
            provenance = fields[6],
        )
    }.getOrNull()?.takeIf { it.version == RecommendationFeedback.CURRENT_VERSION }

    private companion object {
        val ENTRIES = stringSetPreferencesKey("entries")
        const val SEPARATOR = "|"
        const val NO_EXPIRY = "-"
        const val FIELD_COUNT = 7
    }
}
