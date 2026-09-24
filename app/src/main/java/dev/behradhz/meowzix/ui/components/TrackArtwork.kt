package dev.behradhz.meowzix.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private object ArtworkBitmapCache {
    private const val MAX_CACHE_KB = 24 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }
    private val decodeLocks = ConcurrentHashMap<String, Mutex>()

    fun getCached(
        artworkRef: String,
        targetPixels: Int,
    ): ImageBitmap? {
        val key = cacheKey(artworkRef, targetPixels)
        return synchronized(cache) { cache.get(key) }
    }

    suspend fun getOrDecode(
        context: Context,
        artworkRef: String,
        targetPixels: Int,
    ): ImageBitmap? {
        val bucket = artworkTargetBucket(targetPixels)
        val key = cacheKey(artworkRef, bucket)
        synchronized(cache) { cache.get(key) }?.let { return it }

        val mutex = decodeLocks.getOrPut(key) { Mutex() }
        return try {
            mutex.withLock {
                synchronized(cache) { cache.get(key) }?.let { return@withLock it }
                val decoded = decodeSampledArtwork(
                    context = context,
                    uri = Uri.parse(artworkRef),
                    targetPixels = bucket,
                )
                if (decoded != null) {
                    synchronized(cache) { cache.put(key, decoded) }
                }
                decoded
            }
        } finally {
            if (!mutex.isLocked) decodeLocks.remove(key, mutex)
        }
    }

    private fun cacheKey(artworkRef: String, targetPixels: Int): String =
        "$artworkRef#${artworkTargetBucket(targetPixels)}"
}

private data class LoadedArtwork(
    val artworkRef: String?,
    val bitmap: ImageBitmap?,
)

private fun artworkTargetBucket(targetPixels: Int): Int {
    val target = targetPixels.coerceAtLeast(64)
    return ARTWORK_SIZE_BUCKETS.firstOrNull { it >= target } ?: ARTWORK_SIZE_BUCKETS.last()
}

@Composable
private fun rememberArtworkBitmap(
    artworkRef: String?,
    targetPixels: Int,
    retainPreviousWhileLoading: Boolean = false,
): ImageBitmap? {
    val context = LocalContext.current
    val normalizedRef = artworkRef?.takeIf { it.isNotBlank() }
    var loadedArtwork by remember(targetPixels) {
        mutableStateOf(LoadedArtwork(artworkRef = null, bitmap = null))
    }
    val cachedBitmap = remember(normalizedRef, targetPixels) {
        normalizedRef?.let { ArtworkBitmapCache.getCached(it, targetPixels) }
    }

    LaunchedEffect(normalizedRef, targetPixels, retainPreviousWhileLoading) {
        if (normalizedRef == null) {
            loadedArtwork = LoadedArtwork(artworkRef = null, bitmap = null)
            return@LaunchedEffect
        }

        if (cachedBitmap != null) {
            loadedArtwork = LoadedArtwork(artworkRef = normalizedRef, bitmap = cachedBitmap)
            return@LaunchedEffect
        }

        if (!retainPreviousWhileLoading) {
            loadedArtwork = LoadedArtwork(artworkRef = normalizedRef, bitmap = null)
        }

        val decoded = ArtworkBitmapCache.getOrDecode(
            context = context.applicationContext,
            artworkRef = normalizedRef,
            targetPixels = targetPixels,
        )
        loadedArtwork = LoadedArtwork(artworkRef = normalizedRef, bitmap = decoded)
    }

    return when {
        normalizedRef == null -> null
        cachedBitmap != null -> cachedBitmap
        loadedArtwork.artworkRef == normalizedRef -> loadedArtwork.bitmap
        retainPreviousWhileLoading -> loadedArtwork.bitmap
        else -> null
    }
}

/** Pre-decodes adjacent Now Playing covers into the shared artwork cache without rendering them. */
@Composable
internal fun PreloadNowPlayingArtwork(artworkRef: String?) {
    rememberArtworkBitmap(
        artworkRef = artworkRef,
        targetPixels = PLAYER_ARTWORK_TARGET_PIXELS,
        retainPreviousWhileLoading = false,
    )
}

private suspend fun decodeSampledArtwork(
    context: Context,
    uri: Uri,
    targetPixels: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@runCatching null
        }

        var sampleSize = 1
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        while (longestEdge / (sampleSize * 2) >= targetPixels) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
fun TrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetPixels = with(density) { (size * 2f).roundToPx() }
    val bitmap = rememberArtworkBitmap(artworkRef, targetPixels)

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

@Composable
fun ChatAvatar(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetPixels = with(density) { (size * 2f).roundToPx() }
    val bitmap = rememberArtworkBitmap(artworkRef, targetPixels)

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
fun DownloadableTrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 52.dp,
    isOffline: Boolean,
    download: OfflineDownload?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        TrackArtwork(artworkRef, description, size = size)
        if (!isOffline) {
            val active = download?.status == DownloadStatus.DOWNLOADING ||
                download?.status == DownloadStatus.QUEUED
            if (active) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Black.copy(alpha = 0.36f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val total = download?.totalBytes?.takeIf { it > 0L }
                        if (total != null) {
                            CircularProgressIndicator(
                                progress = {
                                    (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.size(size * 0.60f),
                                strokeWidth = 3.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.24f),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(size * 0.60f),
                                strokeWidth = 3.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.24f),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(27.dp)
                        .clickable(onClick = onDownload),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Black.copy(alpha = 0.70f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sharp, high-resolution artwork used as the foreground cover in Now Playing.
 * The image is decoded independently from list thumbnails so the player never reuses a tiny row
 * bitmap. A restrained glass-like border and shadow keep it readable over its own blurred backdrop.
 */
@Composable
fun NowPlayingArtwork(
    artworkRef: String?,
    description: String,
    modifier: Modifier = Modifier,
    showShadow: Boolean = true,
) {
    val bitmap = rememberArtworkBitmap(
        artworkRef = artworkRef,
        targetPixels = PLAYER_ARTWORK_TARGET_PIXELS,
        retainPreviousWhileLoading = true,
    )
    val shape = RoundedCornerShape(28.dp)
    val artworkModifier = if (showShadow) {
        modifier.shadow(
            elevation = 18.dp,
            shape = shape,
            clip = false,
        )
    } else {
        modifier
    }

    Box(
        modifier = artworkModifier
            .clip(shape)
            .background(Color(0xFF191817).copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.035f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.055f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                Color(0xFF2A2826),
                                Color(0xFF171615),
                            ),
                        ),
                    ),
            )
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

/**
 * Full-screen blurred copy of the current cover for Now Playing. This intentionally uses a
 * display-sized high-resolution decode rather than the low-resolution list thumbnail. The cover
 * is overscanned before blur so no transparent blur edges are visible around the screen.
 */
@Composable
fun TrackArtworkBackdrop(
    artworkRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(
        artworkRef = artworkRef,
        targetPixels = BACKDROP_TARGET_PIXELS,
        retainPreviousWhileLoading = true,
    )

    Box(modifier = modifier.background(Color(0xFF101010))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = BLURRED_ARTWORK_SCALE
                        scaleY = BLURRED_ARTWORK_SCALE
                        alpha = 0.92f
                    }
                    .blur(PLAYER_BACKDROP_BLUR),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.26f),
                                0.34f to Color.Black.copy(alpha = 0.18f),
                                0.58f to Color.Black.copy(alpha = 0.34f),
                                0.78f to Color.Black.copy(alpha = 0.58f),
                                1.00f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                Color(0xFF262626),
                                Color(0xFF101010),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.18f),
                                0.50f to Color.Black.copy(alpha = 0.30f),
                                1.00f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
        }
    }
}

private val PLAYER_BACKDROP_BLUR = 52.dp
private val ARTWORK_SIZE_BUCKETS = intArrayOf(128, 256, 512, 1024, 1536, 2048)
private const val PLAYER_ARTWORK_TARGET_PIXELS = 2048
private const val BACKDROP_TARGET_PIXELS = 1800
private const val BLURRED_ARTWORK_SCALE = 1.20f
