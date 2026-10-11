package dev.behradhz.meowzix.data.lyrics

import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline, bounded extraction of common embedded lyric tags. MediaMetadataRetriever does
 * not expose lyrics; Media3 decodes MP3 USLT as an opaque BinaryFrame.
 */
internal object EmbeddedLyricsReader {
    private const val MAX_TAG = 16 * 1024 * 1024
    private const val MAX_TEXT = LrcParser.MAX_CHARS * 4

    fun extract(input: InputStream): String? {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input)
        stream.mark(16)
        val header = exact(stream, 10) ?: return null
        stream.reset()
        return when {
            String(header, 0, 3, Charsets.ISO_8859_1) == "ID3" -> id3(stream)
            String(header, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> flac(stream)
            String(header, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> atoms(stream, null, 0, false)
            else -> null
        }
    }

    private fun id3(stream: InputStream): String? {
        val header = exact(stream, 10) ?: return null
        val version = unsigned(header[3])
        if (version !in 2..4 || (6..9).any { unsigned(header[it]) and 0x80 != 0 }) return null
        val size = syncSafe(header, 6)
        if (size !in 1..MAX_TAG) return null
        val bytes = exact(stream, size) ?: return null
        var cursor = 0
        if (unsigned(header[5]) and 0x40 != 0) {
            if (version == 2 || bytes.size < 4) return null
            val ext = if (version == 4) syncSafe(bytes, 0) else big32(bytes, 0).toInt() + 4
            if (ext < 4 || ext > bytes.size) return null
            cursor = ext
        }
        var plain: String? = null
        var timed: String? = null
        val headerSize = if (version == 2) 6 else 10
        while (cursor + headerSize <= bytes.size) {
            val idLen = if (version == 2) 3 else 4
            val id = String(bytes, cursor, idLen, Charsets.ISO_8859_1)
            if (id.any { !it.isLetterOrDigit() || it.isLowerCase() }) break
            val length = if (version == 2) {
                (unsigned(bytes[cursor + 3]) shl 16) or
                    (unsigned(bytes[cursor + 4]) shl 8) or unsigned(bytes[cursor + 5])
            } else if (version == 4) syncSafe(bytes, cursor + 4)
            else big32(bytes, cursor + 4).toInt()
            val flags = if (version == 2) 0 else unsigned(bytes[cursor + 9])
            cursor += headerSize
            if (length <= 0 || length > bytes.size - cursor) break
            val unsupported = (version == 3 && flags and 0xC0 != 0) ||
                (version == 4 && flags and 0x4D != 0)
            if (!unsupported && length <= MAX_TEXT + 1024 && id in setOf("USLT", "ULT", "SYLT", "SLT", "TXXX", "TXX")) {
                var payload = bytes.copyOfRange(cursor, cursor + length)
                if (unsigned(header[5]) and 0x80 != 0 || version == 4 && flags and 0x02 != 0) {
                    payload = undoUnsynchronization(payload)
                }
                when (id) {
                    "USLT", "ULT" -> if (plain == null) plain = unsynchronised(payload)?.takeIf(::displayable)
                    "SYLT", "SLT" -> if (timed == null) timed = synchronised(payload)?.takeIf(::displayable)
                    "TXXX", "TXX" -> userTextLyrics(payload)?.takeIf(::displayable)?.let { value ->
                        if (LrcParser.parse(value).contentType == LyricsContentType.TIMED) {
                            if (timed == null) timed = value
                        } else if (plain == null) plain = value
                    }
                }
            }
            cursor += length
        }
        return timed ?: plain
    }

    private fun userTextLyrics(bytes: ByteArray): String? {
        if (bytes.size < 3) return null
        val encoding = unsigned(bytes[0])
        val end = terminator(bytes, 1, encoding) ?: return null
        val key = decode(bytes, 1, end, encoding)?.trim()?.uppercase()
            ?.replace("_", "")?.replace("-", "")?.replace(" ", "") ?: return null
        if (key !in setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LRC")) return null
        val start = end + if (encoding == 1 || encoding == 2) 2 else 1
        return decode(bytes, start, bytes.size, encoding)?.trimEnd('\u0000')
    }

    private fun unsynchronised(bytes: ByteArray): String? {
        if (bytes.size < 5) return null
        val encoding = unsigned(bytes[0])
        val start = descriptionEnd(bytes, 4, encoding) ?: return null
        return decode(bytes, start, bytes.size, encoding)?.trimEnd('\u0000')
    }

    private fun synchronised(bytes: ByteArray): String? {
        // SYLT timestamp format 2 is milliseconds; format 1 (MPEG frames) is not a clock.
        if (bytes.size < 7 || unsigned(bytes[4]) != 2) return null
        val encoding = unsigned(bytes[0])
        var cursor = descriptionEnd(bytes, 6, encoding) ?: return null
        val result = StringBuilder()
        var count = 0
        while (cursor < bytes.size && count < LrcParser.MAX_LINES && result.length < LrcParser.MAX_CHARS) {
            val end = terminator(bytes, cursor, encoding) ?: break
            val lyric = decode(bytes, cursor, end, encoding)?.trim().orEmpty()
            cursor = end + if (encoding == 1 || encoding == 2) 2 else 1
            if (cursor + 4 > bytes.size) break
            val ms = big32(bytes, cursor)
            cursor += 4
            if (lyric.isBlank()) continue
            result.append('[').append((ms / 60000L).toString().padStart(2, '0')).append(':')
                .append((ms % 60000L / 1000L).toString().padStart(2, '0')).append('.')
                .append((ms % 1000L).toString().padStart(3, '0')).append(']')
                .append(lyric).append('\n')
            count++
        }
        return result.toString().takeIf(String::isNotBlank)
    }

    private fun descriptionEnd(bytes: ByteArray, start: Int, encoding: Int): Int? =
        terminator(bytes, start, encoding)?.plus(if (encoding == 1 || encoding == 2) 2 else 1)

    private fun terminator(bytes: ByteArray, start: Int, encoding: Int): Int? {
        if (encoding == 1 || encoding == 2) {
            var at = start
            while (at + 1 < bytes.size) {
                if (bytes[at] == 0.toByte() && bytes[at + 1] == 0.toByte()) return at
                at += 2
            }
        } else {
            for (at in start until bytes.size) if (bytes[at] == 0.toByte()) return at
        }
        return null
    }

    private fun decode(bytes: ByteArray, start: Int, end: Int, encoding: Int): String? =
        if (start > end || end > bytes.size) null else when (encoding) {
            0 -> String(bytes, start, end - start, Charsets.ISO_8859_1)
            1 -> String(bytes, start, end - start, Charsets.UTF_16)
            2 -> String(bytes, start, end - start, Charsets.UTF_16BE)
            3 -> String(bytes, start, end - start, Charsets.UTF_8)
            else -> null
        }

    private fun flac(stream: InputStream): String? {
        if (String(exact(stream, 4) ?: return null, Charsets.US_ASCII) != "fLaC") return null
        repeat(128) {
            val header = exact(stream, 4) ?: return null
            val size = (unsigned(header[1]) shl 16) or (unsigned(header[2]) shl 8) or unsigned(header[3])
            val last = unsigned(header[0]) and 0x80 != 0
            if (unsigned(header[0]) and 0x7F == 4 && size <= MAX_TAG) {
                val comments = exact(stream, size) ?: return null
                vorbis(comments)?.let { return it }
            } else if (!skip(stream, size.toLong())) return null
            if (last) return null
        }
        return null
    }

    private fun vorbis(bytes: ByteArray): String? {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun item(): String? {
            if (buffer.remaining() < 4) return null
            val length = buffer.int
            if (length < 0 || length > buffer.remaining() || length > MAX_TEXT) return null
            val raw = ByteArray(length)
            buffer.get(raw)
            return String(raw, Charsets.UTF_8)
        }
        item() ?: return null // vendor
        if (buffer.remaining() < 4) return null
        val count = buffer.int
        if (count !in 0..10_000) return null
        var plain: String? = null
        var timed: String? = null
        repeat(count) {
            val comment = item() ?: return@repeat
            val key = comment.substringBefore('=', "").uppercase()
            val value = comment.substringAfter('=', "")
            if (key in setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LRC") && displayable(value)) {
                if (LrcParser.parse(value).contentType == LyricsContentType.TIMED) timed = value
                else if (plain == null) plain = value
            }
        }
        return timed ?: plain
    }

    /** Traverse MP4 metadata, skipping mdat instead of buffering the song. */
    private fun atoms(input: InputStream, bound: Long?, depth: Int, inLyrics: Boolean): String? {
        if (depth > 8) return null
        var left = bound
        repeat(10_000) {
            if (left != null && left < 8L) return null
            val header = exact(input, 8) ?: return null
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var size = big32(header, 0)
            var headerSize = 8L
            if (size == 1L) {
                val extended = exact(input, 8) ?: return null
                size = (big32(extended, 0) shl 32) or big32(extended, 4)
                headerSize = 16L
            } else if (size == 0L) size = left ?: return null
            if (size < headerSize || left != null && size > left) return null
            val payload = size - headerSize
            val result = when {
                type == "data" && inLyrics && payload in 8L..(MAX_TEXT + 8L) -> {
                    val bytes = exact(input, payload.toInt()) ?: return null
                    String(bytes, 8, bytes.size - 8, Charsets.UTF_8)
                        .trim('\u0000', ' ', '\n', '\r').takeIf(::displayable)
                }
                type in setOf("moov", "udta", "meta", "ilst", "©lyr") -> {
                    val child = Limited(input, payload)
                    if (type == "meta" && !skip(child, 4L)) return null
                    val found = atoms(child, child.remaining, depth + 1, type == "©lyr")
                    if (!skip(child, child.remaining)) return null
                    found
                }
                else -> {
                    if (!skip(input, payload)) return null
                    null
                }
            }
            if (result != null) return result
            left = left?.minus(size)
        }
        return null
    }

    private class Limited(private val stream: InputStream, var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = stream.read()
            if (value >= 0) remaining--
            return value
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = stream.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
        override fun skip(amount: Long): Long {
            val moved = stream.skip(minOf(amount, remaining))
            remaining -= moved
            return moved
        }
    }

    private fun displayable(text: String): Boolean {
        val parsed = LrcParser.parse(text)
        return when (parsed.contentType) {
            LyricsContentType.TIMED -> parsed.timedLines.any { it.text.isNotBlank() }
            LyricsContentType.PLAIN -> parsed.plainLines.any(String::isNotBlank)
            else -> false
        }
    }
    private fun unsigned(value: Byte): Int = value.toInt() and 255
    private fun syncSafe(bytes: ByteArray, index: Int): Int =
        (unsigned(bytes[index]) shl 21) or (unsigned(bytes[index + 1]) shl 14) or
            (unsigned(bytes[index + 2]) shl 7) or unsigned(bytes[index + 3])
    private fun big32(bytes: ByteArray, index: Int): Long =
        (unsigned(bytes[index]).toLong() shl 24) or (unsigned(bytes[index + 1]).toLong() shl 16) or
            (unsigned(bytes[index + 2]).toLong() shl 8) or unsigned(bytes[index + 3]).toLong()
    private fun undoUnsynchronization(bytes: ByteArray): ByteArray {
        val output = ArrayList<Byte>(bytes.size)
        var index = 0
        while (index < bytes.size) {
            output.add(bytes[index])
            if (bytes[index] == 0xFF.toByte() && index + 1 < bytes.size && bytes[index + 1] == 0.toByte()) index++
            index++
        }
        return output.toByteArray()
    }
    private fun exact(stream: InputStream, size: Int): ByteArray? {
        if (size < 0 || size > MAX_TAG) return null
        val bytes = ByteArray(size)
        var index = 0
        while (index < size) {
            val count = stream.read(bytes, index, size - index)
            if (count < 0) return null
            if (count == 0) {
                val one = stream.read()
                if (one < 0) return null
                bytes[index++] = one.toByte()
            } else index += count
        }
        return bytes
    }
    private fun skip(stream: InputStream, count: Long): Boolean {
        if (count < 0) return false
        var left = count
        while (left > 0) {
            val moved = stream.skip(left)
            if (moved > 0) left -= moved
            else if (stream.read() < 0) return false
            else left--
        }
        return true
    }
}
