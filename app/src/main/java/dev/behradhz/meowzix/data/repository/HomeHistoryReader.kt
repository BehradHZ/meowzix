package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.db.HistoryDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Behavioral revision independent of metadata, playback ticks, and download progress. */
data class HomeRecommendationSignals(
    val finalizedOutcomes: Long,
    val favoriteChanges: Long,
)

/** Lightweight Home-only history projection; never materializes the complete listening log. */
@Singleton
class HomeHistoryReader @Inject constructor(
    private val historyDao: HistoryDao,
) {
    fun recommendationSignals(): Flow<HomeRecommendationSignals> =
        historyDao.observeHomeRecommendationSignals().map {
            HomeRecommendationSignals(it.finalizedOutcomes, it.favoriteChanges)
        }

    fun recentTrackIds(eventLimit: Int = 64, trackLimit: Int = 12): Flow<List<UUID>> =
        historyDao.observeRecentHomeSelections(eventLimit.coerceIn(trackLimit, 256)).map { events ->
            events.asSequence()
                .mapNotNull { row -> runCatching { UUID.fromString(row.trackId) }.getOrNull() }
                .distinct()
                .take(trackLimit.coerceAtLeast(1))
                .toList()
        }
}
