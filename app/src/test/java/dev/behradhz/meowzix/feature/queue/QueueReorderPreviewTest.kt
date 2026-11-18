package dev.behradhz.meowzix.feature.queue

import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReorderPreviewTest {
    @Test fun `drop above Next heading promotes first Next song without moving its slot`() {
        val playing = row()
        val manual = row()
        val generated = row()
        val original = indexedQueueItems(listOf(
            playing.copy(origin = QueueItemOrigin.GENERATED),
            manual.copy(origin = QueueItemOrigin.MANUAL),
            generated.copy(origin = QueueItemOrigin.GENERATED),
        )) { true }
        assertEquals(QueueItemOrigin.MANUAL,
            queueDropOrigin(QueueItemOrigin.GENERATED, 149f, 150f))
        assertEquals(QueuePreviewMove(2, 2, QueueItemOrigin.MANUAL),
            resolvePreviewQueueMove(original, original, generated.occurrenceId,
                queueDropOrigin(QueueItemOrigin.GENERATED, 149f, 150f)))
        assertNull(resolvePreviewQueueMove(original, original, generated.occurrenceId,
            queueDropOrigin(QueueItemOrigin.GENERATED, 150f, 150f)))
    }

    @Test fun `moving a generated occurrence inside Queue requests a manual lane`() {
        val playing = row()
        val queued = row()
        val next = row()
        val original = indexedQueueItems(listOf(
            playing.copy(origin = QueueItemOrigin.GENERATED),
            queued.copy(origin = QueueItemOrigin.MANUAL),
            next.copy(origin = QueueItemOrigin.GENERATED),
        )) { true }
        val preview = reorderPreviewItems(original, next.occurrenceId, queued.occurrenceId)
        assertEquals(QueuePreviewMove(2, 1, QueueItemOrigin.MANUAL),
            resolvePreviewQueueMove(original, preview, next.occurrenceId))
    }

    @Test fun `drop without visible section heading uses preview row lane`() {
        assertEquals(QueueItemOrigin.MANUAL,
            queueDropOrigin(QueueItemOrigin.MANUAL, 220f, null))
    }



    @Test fun `Next card becomes Queue as soon as its top edge crosses the heading`() {
        // The center is still below the heading (190 > 160). The old behavior
        // incorrectly required the center itself to move above that heading.
        assertEquals(QueueItemOrigin.MANUAL,
            queueDropOrigin(QueueItemOrigin.GENERATED, 190f, 160f, 72f,
                QueueItemOrigin.GENERATED))
        assertEquals(QueueItemOrigin.GENERATED,
            queueDropOrigin(QueueItemOrigin.GENERATED, 198f, 160f, 72f,
                QueueItemOrigin.GENERATED))
        // The opposite drag direction keeps its existing center-based boundary.
        assertEquals(QueueItemOrigin.GENERATED,
            queueDropOrigin(QueueItemOrigin.MANUAL, 190f, 160f, 72f,
                QueueItemOrigin.MANUAL))
    }

    @Test fun `crossing Next heading inserts at Queue tail without crossing a manual row`() {
        val playing = row().copy(origin = QueueItemOrigin.GENERATED)
        val queued1 = row().copy(origin = QueueItemOrigin.MANUAL)
        val queued2 = row().copy(origin = QueueItemOrigin.MANUAL)
        val next1 = row().copy(origin = QueueItemOrigin.GENERATED)
        val next2 = row().copy(origin = QueueItemOrigin.GENERATED)
        val original = indexedQueueItems(listOf(playing, queued1, queued2, next1, next2)) { true }
        assertEquals(
            QueuePreviewMove(4, 3, QueueItemOrigin.MANUAL),
            resolveQueueBoundaryMove(original, original, next2.occurrenceId,
                QueueItemOrigin.MANUAL, firstNextSlot = 3),
        )
        // Going further above Queue's last song still honors the intended slot.
        val moved = reorderPreviewItems(original, next2.occurrenceId, queued2.occurrenceId)
        assertEquals(
            QueuePreviewMove(4, 2, QueueItemOrigin.MANUAL),
            resolveQueueBoundaryMove(original, moved, next2.occurrenceId,
                QueueItemOrigin.MANUAL, firstNextSlot = 3),
        )
        assertEquals(
            null,
            resolveQueueBoundaryMove(original, original, next2.occurrenceId,
                QueueItemOrigin.GENERATED, firstNextSlot = 3),
        )
    }

    @Test fun `promoting Next does not push the last manual song into the Next section`() {
        val playing = row().copy(origin = QueueItemOrigin.GENERATED)
        val firstManual = row().copy(origin = QueueItemOrigin.MANUAL)
        val lastManual = row().copy(origin = QueueItemOrigin.MANUAL)
        val promoted = row().copy(origin = QueueItemOrigin.GENERATED)
        val next = row().copy(origin = QueueItemOrigin.GENERATED)
        val original = indexedQueueItems(listOf(
            playing, firstManual, lastManual, promoted, next,
        )) { true }
        val preview = reorderPreviewItems(
            original, promoted.occurrenceId, lastManual.occurrenceId,
        )
        val headers = queueSectionStartSlots(preview, 0) { _, indexed ->
            queuePreviewOrigin(indexed.value, promoted.occurrenceId, QueueItemOrigin.MANUAL)
        }
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 4), headers)
        assertEquals(QueueItemOrigin.MANUAL,
            queuePreviewOrigin(lastManual, promoted.occurrenceId, QueueItemOrigin.MANUAL))

        // Crossing the section header before any row midpoint also moves Next
        // past the promoted song while leaving the two original manual entries.
        val atBoundary = queueSectionStartSlots(original, 0) { _, indexed ->
            queuePreviewOrigin(indexed.value, promoted.occurrenceId, QueueItemOrigin.MANUAL)
        }
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 4), atBoundary)
    }

    @Test fun `dragging a queued song does not visually move other queued songs into Next`() {
        val playing = row().copy(origin = QueueItemOrigin.GENERATED)
        val queued1 = row().copy(origin = QueueItemOrigin.MANUAL)
        val queued2 = row().copy(origin = QueueItemOrigin.MANUAL)
        val queued3 = row().copy(origin = QueueItemOrigin.MANUAL)
        val next1 = row().copy(origin = QueueItemOrigin.GENERATED)
        val next2 = row().copy(origin = QueueItemOrigin.GENERATED)
        val original = indexedQueueItems(listOf(
            playing, queued1, queued2, queued3, next1, next2,
        )) { true }
        val crossed = reorderPreviewItems(
            original, queued2.occurrenceId, next2.occurrenceId,
        )
        val preview = keepQueuePreviewSections(
            crossed, 0, queued2.occurrenceId, QueueItemOrigin.MANUAL,
        )
        assertEquals(
            listOf(playing, queued1, queued3, queued2, next1, next2)
                .map { it.occurrenceId },
            preview.map { it.value.occurrenceId },
        )
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 4),
            queueSectionStartSlots(preview, 0))
        assertEquals(listOf(queued1, queued2, queued3).map { it.occurrenceId }.toSet(),
            preview.drop(1).take(3).map { it.value.occurrenceId }.toSet())
    }

    @Test fun `Next drag into Queue promotes only the dragged occurrence`() {
        val playing = row().copy(origin = QueueItemOrigin.GENERATED)
        val queued1 = row().copy(origin = QueueItemOrigin.MANUAL)
        val queued2 = row().copy(origin = QueueItemOrigin.MANUAL)
        val next1 = row().copy(origin = QueueItemOrigin.GENERATED)
        val next2 = row().copy(origin = QueueItemOrigin.GENERATED)
        val original = indexedQueueItems(listOf(
            playing, queued1, queued2, next1, next2,
        )) { true }
        val crossed = reorderPreviewItems(original, next2.occurrenceId, next1.occurrenceId)
        val preview = keepQueuePreviewSections(
            crossed, 0, next2.occurrenceId, QueueItemOrigin.MANUAL,
        )
        assertEquals(listOf(playing, queued1, queued2, next2, next1)
            .map { it.occurrenceId }, preview.map { it.value.occurrenceId })
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 4),
            queueSectionStartSlots(preview, 0) { _, indexed ->
                queuePreviewOrigin(indexed.value, next2.occurrenceId, QueueItemOrigin.MANUAL)
            })
        assertEquals(
            QueuePreviewMove(4, 3, QueueItemOrigin.MANUAL),
            resolveQueueBoundaryMove(original, preview, next2.occurrenceId,
                QueueItemOrigin.MANUAL, firstNextSlot = 3),
        )
        // Crossing only the heading, without another midpoint, is still a move.
        assertEquals(
            QueuePreviewMove(3, 3, QueueItemOrigin.MANUAL),
            resolveQueueBoundaryMove(original, original, next1.occurrenceId,
                QueueItemOrigin.MANUAL, firstNextSlot = 3),
        )
    }

    private fun row(song: UUID = UUID.randomUUID(), occurrence: UUID = UUID.randomUUID()) =
        QueueItem(song, "Song", null, null, occurrenceId = occurrence)

    @Test fun `drag targets crossed midpoints rather than nearest row`() {
        val a = row()
        val b = row()
        val c = row()
        val visible = listOf(
            QueueDragTarget(a.occurrenceId, 1, 100f),
            QueueDragTarget(b.occurrenceId, 2, 180f),
            QueueDragTarget(c.occurrenceId, 3, 260f),
        )
        assertNull(crossedReorderTarget(visible, 1, 180f, 1))
        assertEquals(b.occurrenceId, crossedReorderTarget(visible, 1, 188f, 1))
        assertEquals(c.occurrenceId, crossedReorderTarget(visible, 1, 280f, 1))
    }

    @Test fun `now playing is not a valid destination`() {
        val playing = row()
        val first = row()
        val second = row()
        val visible = listOf(
            QueueDragTarget(playing.occurrenceId, 0, 90f),
            QueueDragTarget(first.occurrenceId, 1, 160f),
            QueueDragTarget(second.occurrenceId, 2, 230f),
        )
        assertEquals(first.occurrenceId, crossedReorderTarget(visible, 2, 120f, 1))
        assertNull(crossedReorderTarget(visible, 1, 60f, 1))
    }

    @Test fun `duplicate songs move by occurrence and commit once`() {
        val repeatedSong = UUID.randomUUID()
        val playing = row()
        val first = row(repeatedSong)
        val middle = row()
        val second = row(repeatedSong)
        val original = indexedQueueItems(listOf(playing, first, middle, second)) { true }
        val preview = reorderPreviewItems(original, second.occurrenceId, first.occurrenceId)
        assertEquals(listOf(playing, second, first, middle).map { it.occurrenceId },
            preview.map { it.value.occurrenceId })
        assertEquals(3 to 1, resolvePreviewMove(original, preview, second.occurrenceId))
        assertEquals(1 to 2, resolvePreviewMove(original,
            reorderPreviewItems(original, first.occurrenceId, middle.occurrenceId),
            first.occurrenceId))
        assertNull(resolvePreviewMove(original, original, second.occurrenceId))
    }

    @Test fun `reorder preview exposes a single heading per lane even with mixed occurrences`() {
        val playing = row().copy(origin = QueueItemOrigin.GENERATED)
        val manual1 = row().copy(origin = QueueItemOrigin.MANUAL)
        val generated1 = row().copy(origin = QueueItemOrigin.GENERATED)
        val manual2 = row().copy(origin = QueueItemOrigin.MANUAL)
        val generated2 = row().copy(origin = QueueItemOrigin.GENERATED)
        val original = indexedQueueItems(
            listOf(playing, manual1, generated1, manual2, generated2)
        ) { true }
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 2),
            queueSectionStartSlots(original, currentIndex = 0))

        val preview = reorderPreviewItems(
            original, generated2.occurrenceId, manual1.occurrenceId
        )
        // Reorder may interleave logical origins: headings remain attached to
        // the original visual lanes, not recreated for each moved occurrence.
        assertEquals(QueueSectionStartSlots(manual = 1, generated = 2),
            queueSectionStartSlots(preview, currentIndex = 0) { slot, row ->
                original.getOrNull(slot)?.value?.origin ?: row.value.origin
            })
    }

    @Test fun `changed queue invalidates an in-flight drag`() {
        val original = indexedQueueItems(listOf(row(), row(), row())) { true }
        assertNull(resolvePreviewMove(original.dropLast(1), original,
            original.last().value.occurrenceId))
    }
}
