package dev.behradhz.meowzix.feature.queue

import dev.behradhz.meowzix.domain.playback.QueueItem

/**
 * Keep each occurrence's original logical queue index after filtering/reordering.
 * A trackId alone cannot identify an occurrence: Play Next may insert duplicates.
 */
internal fun indexedQueueItems(
    items: List<QueueItem>,
    matches: (QueueItem) -> Boolean,
): List<IndexedValue<QueueItem>> = items.withIndex().filter { matches(it.value) }
