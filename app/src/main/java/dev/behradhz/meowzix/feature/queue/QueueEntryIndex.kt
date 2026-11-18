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

/** Render each queue lane heading exactly once, regardless of preview item order. */
internal data class QueueSectionStartSlots(val manual: Int?, val generated: Int?)

internal fun queueSectionStartSlots(
    rows: List<IndexedValue<QueueItem>>,
    currentIndex: Int,
    originAtSlot: (Int, IndexedValue<QueueItem>) ->
        dev.behradhz.meowzix.domain.playback.QueueItemOrigin = { _, entry -> entry.value.origin },
): QueueSectionStartSlots {
    var manual: Int? = null
    var generated: Int? = null
    rows.forEachIndexed { slot, row ->
        if (row.index <= currentIndex) return@forEachIndexed
        when (originAtSlot(slot, row)) {
            dev.behradhz.meowzix.domain.playback.QueueItemOrigin.MANUAL ->
                if (manual == null) manual = slot
            dev.behradhz.meowzix.domain.playback.QueueItemOrigin.GENERATED ->
                if (generated == null) generated = slot
        }
    }
    return QueueSectionStartSlots(manual = manual, generated = generated)
}

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
    draggedCardHeightPx: Float = 0f,
    sourceOrigin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin = slotOrigin,
): dev.behradhz.meowzix.domain.playback.QueueItemOrigin {
    if (!dragCenterY.isFinite() || nextSectionTopY?.isFinite() != true) return slotOrigin
    // A lifted Next card enters Queue as soon as its TOP edge crosses the
    // heading. Its center must not have to reach the last manually queued song.
    // Preserve the existing center-based threshold for Queue -> Next moves.
    val boundaryY = if (sourceOrigin == dev.behradhz.meowzix.domain.playback.QueueItemOrigin.GENERATED)
        dragCenterY - draggedCardHeightPx.coerceAtLeast(0f) / 2f else dragCenterY
    return if (boundaryY < nextSectionTopY) {
        dev.behradhz.meowzix.domain.playback.QueueItemOrigin.MANUAL
    } else {
        dev.behradhz.meowzix.domain.playback.QueueItemOrigin.GENERATED
    }
}

/**
 * Promoting a Next occurrence snaps its *destination* to the first generated
 * slot (the end of Queue) even before a row's midpoint is crossed. Higher
 * manual preview positions are still honored, as are all other drag directions.
 */
internal fun resolveQueueBoundaryMove(
    original: List<IndexedValue<QueueItem>>,
    preview: List<IndexedValue<QueueItem>>,
    occurrenceId: java.util.UUID,
    destinationOrigin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin?,
    firstNextSlot: Int,
): QueuePreviewMove? {
    val resolved = resolvePreviewQueueMove(original, preview, occurrenceId, destinationOrigin)
        ?: return null
    val source = original.firstOrNull { it.value.occurrenceId == occurrenceId } ?: return resolved
    if (source.value.origin != dev.behradhz.meowzix.domain.playback.QueueItemOrigin.GENERATED ||
        resolved.origin != dev.behradhz.meowzix.domain.playback.QueueItemOrigin.MANUAL ||
        firstNextSlot !in original.indices
    ) return resolved
    val previewSlot = preview.indexOfFirst { it.value.occurrenceId == occurrenceId }
    return if (previewSlot >= firstNextSlot) {
        resolved.copy(toIndex = original[firstNextSlot].index)
    } else resolved
}

/**
 * Preserve the lane of every non-dragged occurrence during a preview. A generated
 * occurrence moving into Queue must not visually push a pre-existing manual
 * occurrence under the Next heading.
 */
internal fun queuePreviewOrigin(
    row: QueueItem,
    draggedOccurrenceId: java.util.UUID?,
    hoveredOrigin: dev.behradhz.meowzix.domain.playback.QueueItemOrigin?,
): dev.behradhz.meowzix.domain.playback.QueueItemOrigin =
    if (row.occurrenceId == draggedOccurrenceId && hoveredOrigin != null)
        hoveredOrigin else row.origin

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
