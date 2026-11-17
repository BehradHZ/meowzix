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

/** Visible-row center in LazyColumn viewport coordinates. */
internal data class QueueDragTarget(
    val occurrenceId: java.util.UUID,
    val slot: Int,
    val centerY: Float,
)

/** Resolve crossed row midpoints, excluding the currently playing slot. */
internal fun crossedReorderTarget(
    visible: List<QueueDragTarget>,
    currentSlot: Int,
    draggedCenterY: Float,
    firstMovableSlot: Int,
): java.util.UUID? {
    val marginPx = 6f
    val candidates = visible.filter { it.slot >= firstMovableSlot }
    val below = candidates.filter {
        it.slot > currentSlot && draggedCenterY >= it.centerY + marginPx
    }.maxByOrNull { it.slot }
    val above = candidates.filter {
        it.slot < currentSlot && draggedCenterY <= it.centerY - marginPx
    }.minByOrNull { it.slot }
    return when {
        below == null -> above?.occurrenceId
        above == null -> below.occurrenceId
        else -> if (kotlin.math.abs(draggedCenterY - below.centerY) <=
            kotlin.math.abs(draggedCenterY - above.centerY)) below.occurrenceId
            else above.occurrenceId
    }
}

/** Rearrange only a local visual snapshot, preserving duplicate-song occurrences. */
internal fun reorderPreviewItems(
    rows: List<IndexedValue<QueueItem>>,
    movedOccurrence: java.util.UUID,
    targetOccurrence: java.util.UUID,
): List<IndexedValue<QueueItem>> {
    val from = rows.indexOfFirst { it.value.occurrenceId == movedOccurrence }
    val to = rows.indexOfFirst { it.value.occurrenceId == targetOccurrence }
    if (from < 0 || to < 0 || from == to) return rows
    return rows.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * The section heading is the boundary between manually queued and generated
 * songs. A drop above it targets Queue even if the dragged song still occupies
 * the first generated visual slot.
 */
internal fun queueDropOrigin(
    slotOrigin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin,
    dragCenterY: Float,
    nextSectionTopY: Float?,
): dev.behradhz.meowzix.domain.playback.QueueItemOrigin {
    if (!dragCenterY.isFinite() || nextSectionTopY == null) return slotOrigin
    return if (dragCenterY < nextSectionTopY) {
        dev.behradhz.meowzix.domain.playback.QueueItemOrigin.MANUAL
    } else {
        dev.behradhz.meowzix.domain.playback.QueueItemOrigin.GENERATED
    }
}

internal data class QueuePreviewMove(
    val fromIndex: Int,
    val toIndex: Int,
    val origin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin,
)

/** A same-slot lane change is a real queue mutation, not a no-op. */
internal fun resolvePreviewQueueMove(
    original: List<IndexedValue<QueueItem>>,
    preview: List<IndexedValue<QueueItem>>,
    occurrenceId: java.util.UUID,
    destinationOrigin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin? = null,
): QueuePreviewMove? {
    if (original.size != preview.size ||
        original.map { it.value.occurrenceId }.toSet() !=
            preview.map { it.value.occurrenceId }.toSet()) return null
    val source = original.firstOrNull { it.value.occurrenceId == occurrenceId } ?: return null
    val slot = preview.indexOfFirst { it.value.occurrenceId == occurrenceId }
    val destination = original.getOrNull(slot) ?: return null
    val origin = destinationOrigin ?: destination.value.origin
    if (source.index == destination.index && source.value.origin == origin) return null
    return QueuePreviewMove(source.index, destination.index, origin)
}

/** Convert a finished visual preview to one original-index move. */
internal fun resolvePreviewMove(
    original: List<IndexedValue<QueueItem>>,
    preview: List<IndexedValue<QueueItem>>,
    occurrenceId: java.util.UUID,
): Pair<Int, Int>? {
    if (original.size != preview.size || original.map { it.value.occurrenceId }.toSet() !=
        preview.map { it.value.occurrenceId }.toSet()) return null
    val source = original.firstOrNull { it.value.occurrenceId == occurrenceId } ?: return null
    val slot = preview.indexOfFirst { it.value.occurrenceId == occurrenceId }
    val destination = original.getOrNull(slot) ?: return null
    return if (source.index == destination.index) null else source.index to destination.index
}
