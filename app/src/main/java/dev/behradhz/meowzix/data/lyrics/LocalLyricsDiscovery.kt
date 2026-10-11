package dev.behradhz.meowzix.data.lyrics

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.data.db.LyricsDao
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsRepository
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reacts to the current canonical track's readable sources, including Telegram files once
 * download has completed. Never scans remote URIs and never overrides a manually selected version.
 */
@Singleton
class LocalLyricsDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lyricsDao: LyricsDao,
    private val repository: LyricsRepository,
) {
    suspend fun inspect(trackId: UUID, sources: List<TrackSourceEntity>) = withContext(Dispatchers.IO) {
        val existingLabels = lyricsDao.versionsForTrack(trackId.toString())
            .filter { it.sourceType == LyricsSourceType.EMBEDDED.name }
            .mapNotNull { it.sourceLabel }.toSet()
        for (source in sources) {
            val label = "Detected from " + source.id
            if (label in existingLabels) continue
            val text = try {
                val file = source.localPath?.let(::File) ?: source.contentUri?.let { uri ->
                    Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)
                }
                val sidecar = file?.let(::sidecar)
                if (sidecar != null && displayable(sidecar)) sidecar else {
                    val input = when {
                        !source.contentUri.isNullOrBlank() -> {
                            val uri = Uri.parse(source.contentUri)
                            if (uri.scheme !in setOf("content", "file")) null
                            else context.contentResolver.openInputStream(uri)
                        }
                        file?.isFile == true -> file.inputStream()
                        else -> null
                    }
                    input?.use(EmbeddedLyricsReader::extract)
                }
            } catch (canceled: CancellationException) {
                throw canceled
            } catch (_: Exception) {
                // An inaccessible/unsupported source must not affect playback or the UI.
                null
            }
            if (text != null && displayable(text)) repository.saveEmbedded(trackId, text, label)
        }
    }

    private fun sidecar(audio: File): String? {
        if (!audio.isFile) return null
        val basename = audio.name.substringBeforeLast('.', audio.name)
        val parent = audio.parentFile ?: return null
        for (extension in listOf("lrc", "LRC")) {
            val file = File(parent, basename + "." + extension)
            if (file.isFile && file.length() in 1L..(LrcParser.MAX_CHARS * 4L)) {
                val data = file.readBytes()
                return when {
                    data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte() ->
                        String(data, 2, data.size - 2, Charsets.UTF_16LE)
                    data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte() ->
                        String(data, 2, data.size - 2, Charsets.UTF_16BE)
                    else -> String(data, Charsets.UTF_8)
                }
            }
        }
        return null
    }

    private fun displayable(text: String): Boolean {
        val parsed = LrcParser.parse(text)
        return when (parsed.contentType) {
            LyricsContentType.TIMED -> parsed.timedLines.any { it.text.isNotBlank() }
            LyricsContentType.PLAIN -> parsed.plainLines.any(String::isNotBlank)
            else -> false
        }
    }
}
