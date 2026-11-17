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

    @Test fun `changed queue invalidates an in-flight drag`() {
        val original = indexedQueueItems(listOf(row(), row(), row())) { true }
        assertNull(resolvePreviewMove(original.dropLast(1), original,
            original.last().value.occurrenceId))
    }
}
