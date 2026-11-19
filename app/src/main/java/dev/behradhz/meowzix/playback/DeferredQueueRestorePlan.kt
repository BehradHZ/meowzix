package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackSession
import java.util.UUID

/**
 * Prioritize the current URI's neighbors, but never change the persisted logical
 * list: duplicate occurrences, ordering and manual Queue metadata live there.
 */
internal data class DeferredQueueRestorePlan(
    val logicalIds: List<UUID>,
    val batches: List<List<UUID>>,
) {
    companion object {
        fun from(saved: PersistedPlaybackSession): DeferredQueueRestorePlan? {
            val ids = saved.logicalMediaIds.map { raw ->
                runCatching { UUID.fromString(raw) }.getOrNull() ?: return null
            }
            if (ids.isEmpty()) return null
            val center = saved.logicalCurrentIndex.coerceIn(ids.indices)
            val priority = buildList {
                add(ids[center])
                for (offset in 1..10) ids.getOrNull(center + offset)?.let(::add)
                for (offset in 1..3) ids.getOrNull(center - offset)?.let(::add)
            }.distinct()
            val seen = priority.toHashSet()
            return DeferredQueueRestorePlan(
                logicalIds = ids,
                batches = listOf(priority) + ids.distinct().filterNot { it in seen }.chunked(100),
            )
        }
    }
}

/** A mode change, URI replacement or timeline edit supersedes a pending restore. */
internal fun persistedWindowMatches(
    saved: PersistedPlaybackSession,
    actual: List<Triple<String, String?, UUID?>>,
): Boolean = saved.items.size == actual.size &&
    saved.items.indices.all { index ->
        val expectedOccurrence = saved.logicalOccurrenceIds
            .getOrNull(saved.materializedStartIndex + index)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        actual[index].first == saved.items[index].mediaId &&
            if (expectedOccurrence != null) {
                // An occurrence's local audio source may be repaired during restore.
                actual[index].third == expectedOccurrence
            } else {
                // For legacy snapshots without occurrence IDs, compare URIs too.
                actual[index].second == saved.items[index].uri
            }
    }
