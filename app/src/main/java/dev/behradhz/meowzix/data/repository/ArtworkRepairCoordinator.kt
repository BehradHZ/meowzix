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
import dev.behradhz.meowzix.data.telegram.toAudioCandidate
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
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

        val telegram = telegramDao.telegramSourceForAnyAccountTrack(trackId)
        if (telegram != null) {
            val client = TdLibClientAdapter.activeOrNull()
            if (client != null) {
                val message = runCatching {
                    client.send(TdApi.GetMessage(telegram.chatId, telegram.messageId))
                }.getOrNull()
                val candidate = message?.toAudioCandidate()
                if (candidate != null) {
                    val highQualityRef = candidate.artworkFileId?.let { fileId ->
                        downloadTelegramArtwork(client, trackId, fileId)
                    }
                    if (highQualityRef != null) {
                        libraryDao.setArtworkRef(trackId, highQualityRef, Instant.now().toEpochMilli())
                        if (existingIsPreview && existingRef != null) deletePreview(existingRef)
                        return
                    }

                    if (candidate.artworkMinithumbnail != null && (!existingIsReadable || existingIsPreview)) {
                        val previewRef = writePreview(trackId, candidate.artworkMinithumbnail)
                        if (previewRef != null && previewRef != existingRef) {
                            libraryDao.setArtworkRef(trackId, previewRef, Instant.now().toEpochMilli())
                        }
                        // Keep the preview as a non-destructive fallback. A later visible/now-playing
                        // request can still upgrade it when TDLib exposes the full thumbnail.
                        return
                    }
                }
            }
        }

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
        val downloaded = runCatching {
            client.send(TdApi.DownloadFile(fileId, ARTWORK_PRIORITY, 0L, 0L, true))
        }.getOrNull() ?: return null
        val path = downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return null
        val source = File(path).takeIf(File::isFile) ?: return null
        artworkDirectory.mkdirs()
        val target = File(artworkDirectory, "$trackId.artwork")
        return runCatching {
            source.inputStream().buffered().use { input ->
                target.outputStream().buffered().use(input::copyTo)
            }
            Uri.fromFile(target).toString()
        }.getOrNull()
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
        val target = File(previewDirectory, "$trackId.jpg")
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
