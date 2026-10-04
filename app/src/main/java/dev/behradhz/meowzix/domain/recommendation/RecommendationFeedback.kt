package dev.behradhz.meowzix.domain.recommendation

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class RecommendationFeedbackAction {
    MORE_LIKE_THIS,
    SUGGEST_LESS,
    SNOOZE,
}

enum class RecommendationFeedbackScope {
    SMART_RECOMMENDATIONS,
}

data class RecommendationFeedback(
    val trackId: UUID,
    val action: RecommendationFeedbackAction,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val scope: RecommendationFeedbackScope = RecommendationFeedbackScope.SMART_RECOMMENDATIONS,
    val version: Int = CURRENT_VERSION,
    val provenance: String = USER_PROVENANCE,
) {
    fun isActive(now: Instant): Boolean = expiresAt?.isAfter(now) != false

    companion object {
        const val CURRENT_VERSION = 1
        const val USER_PROVENANCE = "user"
        val DEFAULT_SNOOZE: Duration = Duration.ofHours(24)
    }
}

data class RecommendationFeedbackEffect(
    val moreLikeThis: Boolean = false,
    val suggestLess: Boolean = false,
    val snoozedUntil: Instant? = null,
) {
    val excluded: Boolean get() = snoozedUntil != null
}

interface RecommendationFeedbackRepository {
    val feedback: Flow<List<RecommendationFeedback>>

    suspend fun snapshot(now: Instant = Instant.now()): List<RecommendationFeedback>
    suspend fun set(
        trackId: UUID,
        action: RecommendationFeedbackAction,
        now: Instant = Instant.now(),
        snoozeDuration: Duration = RecommendationFeedback.DEFAULT_SNOOZE,
    )
    suspend fun undo(trackId: UUID, action: RecommendationFeedbackAction? = null)
    suspend fun clearExpired(now: Instant = Instant.now())
    suspend fun clearAll()
}

fun List<RecommendationFeedback>.effects(now: Instant): Map<UUID, RecommendationFeedbackEffect> =
    filter { it.isActive(now) }.groupBy { it.trackId }.mapValues { (_, rows) ->
        RecommendationFeedbackEffect(
            moreLikeThis = rows.any { it.action == RecommendationFeedbackAction.MORE_LIKE_THIS },
            suggestLess = rows.any { it.action == RecommendationFeedbackAction.SUGGEST_LESS },
            snoozedUntil = rows.filter { it.action == RecommendationFeedbackAction.SNOOZE }
                .mapNotNull { it.expiresAt }
                .maxOrNull(),
        )
    }
