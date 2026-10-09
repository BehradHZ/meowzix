package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import dev.behradhz.meowzix.domain.recommendation.RecommendationSectionKind
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MediaBrowseMix(
    val id: String,
    val title: String,
    val trackIds: List<UUID>,
)

/**
 * Explicitly cached projection of the existing local recommendation engine for media browsers.
 *
 * Browsing never recalculates recommendations from Compose recomposition. The cache is invalidated
 * by a short TTL, while the recommendation engine itself remains the single ranking implementation.
 */
interface MediaBrowseMixProvider {
    suspend fun mixes(): List<MediaBrowseMix>

    data object Empty : MediaBrowseMixProvider {
        override suspend fun mixes(): List<MediaBrowseMix> = emptyList()
    }
}

@Singleton
class RecommendationMediaBrowseMixProvider @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val settingsRepository: SettingsRepository,
) : MediaBrowseMixProvider {
    private val mutex = Mutex()
    private var cache: Cache? = null

    override suspend fun mixes(): List<MediaBrowseMix> {
        if (!settingsRepository.recommendationPreferenceSettings.first().smartRecommendationsEnabled) {
            mutex.withLock { cache = null }
            return emptyList()
        }

        val now = System.currentTimeMillis()
        mutex.withLock {
            cache?.takeIf { now - it.generatedAtEpochMs < CACHE_TTL_MS }?.let { return it.mixes }
        }

        val sections = recommendationEngine.sections(seed = now / CACHE_TTL_MS)
        val mixes = sections
            .asSequence()
            .filter { it.kind in EXPOSED_KINDS && it.items.isNotEmpty() }
            .map { section ->
                MediaBrowseMix(
                    id = section.kind.name,
                    title = titleFor(section.kind),
                    trackIds = section.items.map { it.trackId }.distinct().take(MAX_MIX_TRACKS),
                )
            }
            .filter { it.trackIds.isNotEmpty() }
            .toList()

        mutex.withLock { cache = Cache(now, mixes) }
        return mixes
    }

    private data class Cache(
        val generatedAtEpochMs: Long,
        val mixes: List<MediaBrowseMix>,
    )

    private companion object {
        const val CACHE_TTL_MS = 60_000L
        const val MAX_MIX_TRACKS = 100

        val EXPOSED_KINDS = setOf(
            RecommendationSectionKind.FOR_YOU_NOW,
            RecommendationSectionKind.TIME_MIX,
            RecommendationSectionKind.REDISCOVER,
            RecommendationSectionKind.HIDDEN_GEMS,
            RecommendationSectionKind.ON_REPEAT,
        )

        fun titleFor(kind: RecommendationSectionKind): String = when (kind) {
            RecommendationSectionKind.FOR_YOU_NOW -> "For You Now"
            RecommendationSectionKind.TIME_MIX -> "Time Mix"
            RecommendationSectionKind.REDISCOVER -> "Rediscover"
            RecommendationSectionKind.HIDDEN_GEMS -> "Hidden Gems"
            RecommendationSectionKind.ON_REPEAT -> "On Repeat"
            else -> kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
    }
}
