package dev.behradhz.meowzix.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun `parses bom crlf metadata offset and common fractions`() {
        val parsed = LrcParser.parse(
            "\uFEFF[ar:Artist]\r\n[ti:Title]\r\n[offset:+250]\r\n[00:01.5]One\r\n[00:02.25]Two\r\n[00:03.125]Three",
        )
        assertEquals(LyricsContentType.TIMED, parsed.contentType)
        assertEquals("Artist", parsed.metadata.artist)
        assertEquals(250L, parsed.metadata.fileOffsetMs)
        assertEquals(listOf(1_750L, 2_500L, 3_375L), parsed.timedLines.map { it.timeMs })
    }

    @Test
    fun `positive file offset makes lines appear later and negative earlier`() {
        val later = LrcParser.parse("[offset:500]\n[00:01.00]line")
        val earlier = LrcParser.parse("[offset:-500]\n[00:01.00]line")
        assertEquals(1_500L, later.timedLines.single().timeMs)
        assertEquals(500L, earlier.timedLines.single().timeMs)
    }

    @Test
    fun `multiple timestamps duplicate text and equal timestamps keep source order`() {
        val parsed = LrcParser.parse("[00:02.00][00:04.00]chorus\n[00:02.00]second")
        assertEquals(listOf(2_000L, 2_000L, 4_000L), parsed.timedLines.map { it.timeMs })
        assertEquals(listOf("chorus", "second", "chorus"), parsed.timedLines.map { it.text })
        assertTrue(parsed.timedLines.zipWithNext().all { (a, b) ->
            a.timeMs < b.timeMs || a.originalOrder < b.originalOrder
        })
    }

    @Test
    fun `plain text remains plain and does not fabricate timing`() {
        val parsed = LrcParser.parse("First line\n\nSecond line")
        assertEquals(LyricsContentType.PLAIN, parsed.contentType)
        assertTrue(parsed.timedLines.isEmpty())
        assertEquals(listOf("First line", "Second line"), parsed.plainLines)
    }

    @Test
    fun `empty timed content is instrumental`() {
        val parsed = LrcParser.parse("[00:10.00]\n[00:20.00]")
        assertEquals(LyricsContentType.INSTRUMENTAL, parsed.contentType)
    }

    @Test
    fun `malformed timestamp is ignored deterministically`() {
        val parsed = LrcParser.parse("[00:99.00]bad\n[00:01.00]good")
        assertEquals(LyricsContentType.TIMED, parsed.contentType)
        assertEquals(listOf("good"), parsed.timedLines.map { it.text })
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun `active lookup uses effective user delay and latest equal timestamp`() {
        val raw = LrcParser.parse("[00:01.00]a\n[00:02.00]b\n[00:02.00]c").timedLines
        val delayed = raw.map { it.copy(timeMs = it.timeMs + 300L) }
        assertEquals(-1, LyricsTiming.activeLineIndex(delayed, 1_000L))
        assertEquals(0, LyricsTiming.activeLineIndex(delayed, 1_300L))
        assertEquals(2, LyricsTiming.activeLineIndex(delayed, 2_300L))
    }

    @Test
    fun `oversized input is rejected without parsing`() {
        val parsed = LrcParser.parse("x".repeat(LrcParser.MAX_CHARS + 1))
        assertEquals(LyricsContentType.INVALID, parsed.contentType)
    }
}
