package dev.behradhz.meowzix.data.recommendation

import java.util.UUID
import kotlin.random.Random

/**
 * Keeps expensive recommendation scoring bounded while retaining behavior-rich candidates and a
 * representative exploration sample from the caller's complete allowed set.
 */
internal fun reduceRecommendationCandidateIds(
    allowedTrackIds: List<UUID>,
    priorityTrackIds: List<UUID>,
    limit: Int,
    seed: Long,
): List<UUID> {
    val boundedLimit = limit.coerceAtLeast(1)
    val allowed = LinkedHashSet<UUID>(allowedTrackIds.size).apply { addAll(allowedTrackIds) }.toList()
    if (allowed.size <= boundedLimit) return allowed

    val allowedSet = allowed.toHashSet()
    val priorityBudget = (boundedLimit / 3).coerceAtLeast(1)
    val priority = priorityTrackIds.asSequence()
        .filter(allowedSet::contains)
        .distinct()
        .take(priorityBudget)
        .toList()
    val prioritySet = priority.toHashSet()
    val sampleLimit = boundedLimit - priority.size
    if (sampleLimit <= 0) return priority.take(boundedLimit)

    // Reservoir sampling is O(N) over UUIDs but O(limit) in memory and never materializes Track,
    // TrackSource, history-stat, acoustic-feature, or model-feature objects for the full library.
    val random = Random(seed)
    val reservoir = ArrayList<UUID>(sampleLimit)
    var seen = 0
    for (trackId in allowed) {
        if (trackId in prioritySet) continue
        seen += 1
        if (reservoir.size < sampleLimit) {
            reservoir += trackId
        } else {
            val index = random.nextInt(seen)
            if (index < sampleLimit) reservoir[index] = trackId
        }
    }
    return priority + reservoir
}
