package dev.behradhz.meowzix.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import dev.behradhz.meowzix.data.telegram.findTelegramProfileAudio
import dev.behradhz.meowzix.data.telegram.toAudioCandidate
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Singleton
class ArtworkRepairCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val telegramDao: TelegramDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val artworkDirectory = File(context.filesDir, "artwork")
    private val previewDirectory = File(context.cacheDir, "artwork-preview")

    fun prefetch(trackIds: List<UUID>) {
        trackIds.asSequence()
            .map(UUID::toString)
            .distinct()
            .filter(inFlight::add)
            .forEach { trackId ->
                scope.launch {
                    try {
                        repair(trackId)
                    } finally {
                        inFlight.remove(trackId)
                    }
                }
            }
    }

    private suspend fun repair(trackId: String) {
        val track = libraryDao.trackById(trackId) ?: return
        val existingRef = track.artworkRef
        val existingIsPreview = existingRef?.contains("/artwork-preview/") == true
        val existingIsReadable = existingRef?.let(::isReadableArtwork) == true
        val existingIsFinalAppArtwork = existingRef?.contains("/files/artwork/") == true

        if (existingIsReadable && existingIsFinalAppArtwork) return

        val client = TdLibClientAdapter.activeOrNull()
        if (client != null) {
            // A logical Track can have several Telegram occurrences. A newer occurrence may not
            // carry artwork even when an older/alternate upload does, so inspect every usable one.
            val candidates = telegramDao.telegramSourcesForAnyAccountTrack(trackId).mapNotNull { source ->
                try {
                    if (source.messageId < 0L) {
                        findTelegramProfileAudio(
                            client,
                            source.chatId,
                            source.messageId,
                            source.tdPersistentFileId,
                        )?.toAudioCandidate(source.messageId)
                    } else {
                        client.send(TdApi.GetMessage(source.chatId, source.messageId)).toAudioCandidate()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }

            // Prefer a primary album thumbnail from any occurrence, then TDLib's
            // external album-cover variants (which are supplied when the file has no embedded art).
            val artworkFileIds = candidates.mapNotNull { it.artworkFileId } +
                candidates.flatMap { it.additionalArtworkFileIds }
            for (fileId in artworkFileIds.distinct().filter { it > 0 }) {
                val highQualityRef = downloadTelegramArtwork(client, trackId, fileId) ?: continue
                libraryDao.setArtworkRef(trackId, highQualityRef, Instant.now().toEpochMilli())
                if (existingIsPreview && existingRef != null) deletePreview(existingRef)
                return
            }

            // Some send-as-file uploads have no Telegram thumbnail but do contain embedded ID3
            // artwork. Use it only when TDLib has already completed the audio download: artwork
            // repair must never trigger a full audio download just to obtain a cover.
            for (candidate in candidates.distinctBy { it.fileId }) {
                if (candidate.fileId <= 0) continue
                val embedded = embeddedArtworkFromCompletedTelegramFile(client, candidate.fileId) ?: continue
                val ref = writeFinalArtwork(trackId, embedded) ?: continue
                libraryDao.setArtworkRef(trackId, ref, Instant.now().toEpochMilli())
                if (existingIsPreview && existingRef != null) deletePreview(existingRef)
                return
            }

            // Keep the best available low-resolution image as a fallback while waiting for a
            // full thumbnail. Do not replace a readable local cover with a tiny Telegram preview.
            val minithumbnail = candidates
                .mapNotNull { it.artworkMinithumbnail }
                .maxByOrNull { it.size }
            if (minithumbnail != null && (!existingIsReadable || existingIsPreview)) {
                val previewRef = writePreview(trackId, minithumbnail)
                if (previewRef != null && previewRef != existingRef) {
                    libraryDao.setArtworkRef(trackId, previewRef, Instant.now().toEpochMilli())
                    if (existingIsPreview && existingRef != null) deletePreview(existingRef)
                }
                return
            }
        }

        // Local embedded artwork remains the final source-agnostic fallback.
        val embedded = libraryDao.sourcesForTrack(trackId)
            .asSequence()
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .mapNotNull { source ->
                when {
                    !source.localPath.isNullOrBlank() -> embeddedArtworkFromPath(source.localPath)
                    !source.contentUri.isNullOrBlank() -> embeddedArtworkFromUri(source.contentUri)
                    else -> null
                }
            }
            .firstOrNull()

        if (embedded != null) {
            val ref = writeFinalArtwork(trackId, embedded) ?: return
            libraryDao.setArtworkRef(trackId, ref, Instant.now().toEpochMilli())
            if (existingIsPreview && existingRef != null) deletePreview(existingRef)
            return
        }

        // Never delete a readable preview just because a higher-quality source is temporarily
        // unavailable. Only clear genuinely broken references so the UI can use its placeholder.
        if (!existingRef.isNullOrBlank() && !existingIsReadable) {
            libraryDao.setArtworkRef(trackId, null, Instant.now().toEpochMilli())
        }
    }

    private suspend fun downloadTelegramArtwork(
        client: TdLibClientAdapter,
        trackId: String,
        fileId: Int,
    ): String? {
        val downloaded = try {
            client.send(TdApi.DownloadFile(fileId, ARTWORK_PRIORITY, 0L, 0L, true))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        val path = downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return null
        val source = File(path).takeIf { it.isFile && isImageFile(it) } ?: return null
        artworkDirectory.mkdirs()
        val target = File(artworkDirectory, "$trackId.artwork")
        return runCatching {
            source.inputStream().buffered().use { input ->
                target.outputStream().buffered().use(input::copyTo)
            }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private suspend fun embeddedArtworkFromCompletedTelegramFile(
        client: TdLibClientAdapter,
        fileId: Int,
    ): ByteArray? {
        val file = try {
            client.send(TdApi.GetFile(fileId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (!file.local.isDownloadingCompleted || file.local.path.isBlank()) return null
        return embeddedArtworkFromPath(file.local.path)
    }

    private fun writeFinalArtwork(trackId: String, bytes: ByteArray): String? {
        if (!isImage(bytes)) return null
        artworkDirectory.mkdirs()
        val target = File(artworkDirectory, "$trackId.artwork")
        return runCatching {
            target.writeBytes(bytes)
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private fun writePreview(trackId: String, bytes: ByteArray): String? {
        if (!isImage(bytes)) return null
        previewDirectory.mkdirs()
        val target = File(previewDirectory, "$trackId-${bytes.contentHashCode()}.jpg")
        return runCatching {
            target.writeBytes(bytes)
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private fun embeddedArtworkFromPath(path: String): ByteArray? = runCatching {
        val file = File(path)
        if (!file.isFile) return@runCatching null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.embeddedPicture?.takeIf(::isImage)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun embeddedArtworkFromUri(value: String): ByteArray? = runCatching {
        val uri = Uri.parse(value)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.takeIf(::isImage)
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun isReadableArtwork(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            options.outWidth > 0 && options.outHeight > 0
        } ?: false
    }.getOrDefault(false)

    private fun isImageFile(file: File): Boolean = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.outWidth > 0 && options.outHeight > 0
    }.getOrDefault(false)

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun deletePreview(value: String) {
        runCatching {
            val candidate = Uri.parse(value).path?.let(::File)?.canonicalFile ?: return@runCatching
            val root = previewDirectory.canonicalFile
            if (candidate.path.startsWith(root.path)) candidate.delete()
        }
    }

    private companion object {
        const val ARTWORK_PRIORITY = 3
    }
}
