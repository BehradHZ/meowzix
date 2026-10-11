package dev.behradhz.meowzix.data.lyrics

import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedLyricsReaderTest {
    @Test fun id3v23UnsynchronisedLyricsAppearAsPlainText() {
        val lyric = "First line\nSecond line"
        val tag = id3("USLT", byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + lyric.toByteArray())
        assertEquals(lyric, EmbeddedLyricsReader.extract(tag.inputStream()))
    }

    @Test fun id3v24SynchronisedLyricsKeepTheirTimecodes() {
        val body = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(2, 1, 0) +
            "Verse".toByteArray() + byteArrayOf(0) + int32(1250) +
            "Chorus".toByteArray() + byteArrayOf(0) + int32(2750)
        val tag = id3("SYLT", body, version = 4)
        val parsed = LrcParser.parse(EmbeddedLyricsReader.extract(tag.inputStream()).orEmpty())
        assertEquals(LyricsContentType.TIMED, parsed.contentType)
        assertEquals(listOf(1250L, 2750L), parsed.timedLines.map { it.timeMs })
        assertEquals(listOf("Verse", "Chorus"), parsed.timedLines.map { it.text })
    }

    @Test fun flacVorbisLyricsAreAutomaticallyRead() {
        val vendor = "Meowzix".toByteArray()
        val comment = "LYRICS=[00:01.00]Hello".toByteArray()
        val block = little(vendor.size) + vendor + little(1) + little(comment.size) + comment
        val flac = "fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0, 0, block.size.toByte()) + block
        assertEquals("[00:01.00]Hello", EmbeddedLyricsReader.extract(flac.inputStream()))
    }

    @Test fun m4aAppleLyricsAtomIsReadWithoutReadingMdatPayload() {
        val lyric = "[00:01.00]MP4 lyric"
        val data = atom("data", ByteArray(8) + lyric.toByteArray())
        val payload = atom("ftyp", "M4A ".toByteArray()) +
            atom("mdat", ByteArray(32)) +
            atom("moov", atom("udta", atom("meta",
                ByteArray(4) + atom("ilst", atom("©lyr", data)))))
        assertEquals(lyric, EmbeddedLyricsReader.extract(payload.inputStream()))
    }

    @Test fun userDefinedId3LyricsAreDetected() {
        val text = "[00:02.00]Timed from TXXX"
        val bytes = byteArrayOf(3) + "SYNCEDLYRICS".toByteArray() + byteArrayOf(0) + text.toByteArray()
        assertEquals(text, EmbeddedLyricsReader.extract(id3("TXXX", bytes).inputStream()))
    }

    @Test fun malformedAndUnrelatedTagDoesNotClaimLyrics() {
        assertNull(EmbeddedLyricsReader.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
        assertNull(EmbeddedLyricsReader.extract(id3("TIT2", byteArrayOf(3) + "Title".toByteArray()).inputStream()))
        val truncated = "ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 5, 0) // declared tag has no body
        assertNull(EmbeddedLyricsReader.extract(truncated.inputStream()))
    }

    private fun id3(name: String, body: ByteArray, version: Int = 3): ByteArray {
        val frameSize = if (version == 4) safe(body.size) else int32(body.size)
        val frame = name.toByteArray() + frameSize + byteArrayOf(0, 0) + body
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + safe(frame.size) + frame
    }

    private fun atom(name: String, bytes: ByteArray): ByteArray =
        int32(bytes.size + 8) + name.toByteArray(Charsets.ISO_8859_1) + bytes

    private fun int32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte(),
    )
    private fun safe(value: Int) = byteArrayOf(
        ((value ushr 21) and 127).toByte(), ((value ushr 14) and 127).toByte(),
        ((value ushr 7) and 127).toByte(), (value and 127).toByte(),
    )
    private fun little(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
