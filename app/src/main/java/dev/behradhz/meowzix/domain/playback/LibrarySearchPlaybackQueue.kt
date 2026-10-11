package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/**
 * Search filters only visible rows. The input must be the unfiltered library IDs in display order.
 * For an ordered search selection, rotate once so the chosen track plays first and all tracks
 * remain in the queue; non-search selections retain their previous tail-only semantics.
 */
fun buildLibraryPlaybackQueue(
    orderedLibraryIds: List<UUID>,
    selectedId: UUID,
    mode: PlaybackMode,
    fromSearch: Boolean,
): List<UUID> {
    val ids = orderedLibraryIds.distinct()
    val selectedIndex = ids.indexOf(selectedId)
    if (selectedIndex < 0) return listOf(selectedId)

    return when {
        mode != PlaybackMode.ORDERED -> ids
        fromSearch -> ids.drop(selectedIndex) + ids.take(selectedIndex)
        else -> ids.drop(selectedIndex)
    }
}
