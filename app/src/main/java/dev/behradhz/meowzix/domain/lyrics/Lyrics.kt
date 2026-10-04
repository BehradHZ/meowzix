package dev.behradhz.meowzix.domain.lyrics

import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class LyricsContentType { TIMED, PLAIN, INSTRUMENTAL, INVALID }
enum class LyricsSourceType { USER_LRC, USER_PASTE, EMBEDDED }

data class TimedLyricLine(
    val timeMs: Long,
    val text: String,
    val originalOrder: Int,
)

data class LyricsMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val by: String? = null,
    val fileOffsetMs: Long = 0L,
)

data class ParsedLyrics(
    val contentType: LyricsContentType,
    val timedLines: List<TimedLyricLine> = emptyList(),
    val plainLines: List<String> = emptyList(),
    val metadata: LyricsMetadata = LyricsMetadata(),
    val warnings: List<String> = emptyList(),
)

data class LyricsVersion(
    val id: UUID,
    val trackId: UUID,
    val sourceType: LyricsSourceType,
    val sourceLabel: String?,
    val rawText: String,
    val parsed: ParsedLyrics,
    val selected: Boolean,
    val userSelected: Boolean,
    val userDelayMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun effectiveLines(): List<TimedLyricLine> = parsed.timedLines.map { line ->
        line.copy(timeMs = line.timeMs + userDelayMs)
    }
}

data class TrackLyrics(
    val trackId: UUID,
    val selected: LyricsVersion? = null,
    val versions: List<LyricsVersion> = emptyList(),
)

interface LyricsRepository {
    fun observe(trackId: UUID): Flow<TrackLyrics>
    suspend fun importLrc(trackId: UUID, rawText: String, sourceLabel: String? = null): UUID
    suspend fun savePlainText(trackId: UUID, text: String): UUID
    suspend fun saveEmbedded(trackId: UUID, rawText: String, sourceLabel: String? = null): UUID
    suspend fun selectVersion(trackId: UUID, versionId: UUID)
    suspend fun setUserDelay(versionId: UUID, delayMs: Long)
    suspend fun deleteVersion(trackId: UUID, versionId: UUID)
}

object LyricsTiming {
    /** Positive user delay makes the lyric appear later. Equal timestamps resolve by stable order. */
    fun activeLineIndex(lines: List<TimedLyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
}

object LrcParser {
    const val MAX_CHARS = 256 * 1024
    const val MAX_LINES = 10_000

    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val metadata = Regex("^\\[([A-Za-z]+):(.*)]$")

    fun parse(input: String): ParsedLyrics {
        if (input.length > MAX_CHARS) {
            return ParsedLyrics(LyricsContentType.INVALID, warnings = listOf("Lyrics file is too large"))
        }
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val sourceLines = normalized.split('\n')
        if (sourceLines.size > MAX_LINES) {
            return ParsedLyrics(LyricsContentType.INVALID, warnings = listOf("Lyrics file has too many lines"))
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null
        var offsetMs = 0L
        val rawTimed = ArrayList<Triple<Long, String, Int>>()
        val plain = ArrayList<String>()
        val warnings = ArrayList<String>()
        var order = 0

        sourceLines.forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trimEnd()
            val meta = metadata.matchEntire(line.trim())
            if (meta != null && timestamp.find(line) == null) {
                val key = meta.groupValues[1].lowercase()
                val value = meta.groupValues[2].trim()
                when (key) {
                    "ti" -> title = value.takeIf(String::isNotBlank)
                    "ar" -> artist = value.takeIf(String::isNotBlank)
                    "al" -> album = value.takeIf(String::isNotBlank)
                    "by" -> by = value.takeIf(String::isNotBlank)
                    "offset" -> offsetMs = value.toLongOrNull() ?: run {
                        warnings += "Invalid offset on line ${lineIndex + 1}"
                        offsetMs
                    }
                }
                return@forEachIndexed
            }

            val matches = timestamp.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val text = line.substring(matches.last().range.last + 1).trim()
                matches.forEach { match ->
                    parseTimestamp(match)?.let { rawTimed += Triple(it, text, order++) }
                        ?: run { warnings += "Invalid timestamp on line ${lineIndex + 1}" }
                }
            } else if (line.isNotBlank()) {
                if (line.trimStart().startsWith("[") && line.contains(':')) {
                    warnings += "Ignored malformed LRC line ${lineIndex + 1}"
                } else {
                    plain += line
                }
            }
        }

        val timed = rawTimed.map { (time, text, originalOrder) ->
            TimedLyricLine(
                timeMs = time + offsetMs,
                text = text,
                originalOrder = originalOrder,
            )
        }.sortedWith(compareBy<TimedLyricLine> { it.timeMs }.thenBy { it.originalOrder })

        val type = when {
            timed.any { it.text.isNotBlank() } -> LyricsContentType.TIMED
            timed.isNotEmpty() -> LyricsContentType.INSTRUMENTAL
            plain.isNotEmpty() -> LyricsContentType.PLAIN
            normalized.isBlank() -> LyricsContentType.INSTRUMENTAL
            else -> LyricsContentType.INVALID
        }
        return ParsedLyrics(
            contentType = type,
            timedLines = timed,
            plainLines = plain,
            metadata = LyricsMetadata(title, artist, album, by, offsetMs),
            warnings = warnings,
        )
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val minute = match.groupValues[1].toLongOrNull() ?: return null
        val second = match.groupValues[2].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val fractionText = match.groupValues[3]
        val millis = when (fractionText.length) {
            0 -> 0L
            1 -> fractionText.toLong() * 100L
            2 -> fractionText.toLong() * 10L
            else -> fractionText.take(3).padEnd(3, '0').toLong()
        }
        return minute * 60_000L + second * 1_000L + millis
    }
}
