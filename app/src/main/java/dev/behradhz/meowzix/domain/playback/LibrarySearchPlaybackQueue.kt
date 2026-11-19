package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/**
 * Search only filters visible rows, not the eligible playback catalog. Starting from
 * any Library song traverses the entire library once, wrapping at the end in ORDERED
 * mode and leaving all distinct candidates eligible in both shuffle modes.
 */
@Suppress("UNUSED_PARAMETER") // Search no longer changes Library cycle coverage.
fun buildLibraryPlaybackQueue(
    orderedLibraryIds: List<UUID>,
    selectedId: UUID,
    mode: PlaybackMode,
    fromSearch: Boolean,
): List<UUID> {
    val ids = orderedLibraryIds.distinct()
    val selectedIndex = ids.indexOf(selectedId)
    if (selectedIndex < 0) return listOf(selectedId) + ids

    return when (mode) {
        PlaybackMode.ORDERED -> ids.drop(selectedIndex) + ids.take(selectedIndex)
        PlaybackMode.PURE_SHUFFLE, PlaybackMode.SMART_SHUFFLE -> ids
    }
}
