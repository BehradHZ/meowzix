package dev.behradhz.meowzix.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
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


/**
 * Pre-blurred, bounded artwork cache for the player background.
 *
 * Blur runs once per artwork on Dispatchers.Default, not on the RenderThread every frame.
 * Keep it independent of the full-resolution foreground artwork cache.
 */
private object BlurredBackdropCache {
    private const val MAX_CACHE_KB = 6 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }
    private val decodeLocks = ConcurrentHashMap<String, Mutex>()

    fun getCached(ref: String): ImageBitmap? = synchronized(cache) { cache.get(ref) }

    suspend fun getOrCreate(context: Context, ref: String): ImageBitmap? {
        getCached(ref)?.let { return it }
        val mutex = decodeLocks.getOrPut(ref) { Mutex() }
        return try {
            mutex.withLock {
                getCached(ref)?.let { return@withLock it }
                val decoded = decodeSampledArtwork(
                    context = context,
                    uri = Uri.parse(ref),
                    targetPixels = BACKDROP_TARGET_PIXELS,
                ) ?: return@withLock null
                val blurred = withContext(Dispatchers.Default) {
                    preblurBackdrop(decoded.asAndroidBitmap()).asImageBitmap()
                }
                synchronized(cache) { cache.put(ref, blurred) }
                blurred
            }
        } finally {
            if (!mutex.isLocked) decodeLocks.remove(ref, mutex)
        }
    }
}

@Composable
private fun rememberBlurredBackdrop(ref: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val normalized = ref?.takeIf(String::isNotBlank)
    var loaded by remember { mutableStateOf(LoadedArtwork(null, null)) }
    val cached = remember(normalized) { normalized?.let(BlurredBackdropCache::getCached) }
    LaunchedEffect(normalized) {
        if (normalized == null) {
            loaded = LoadedArtwork(null, null)
        } else {
            val image = cached ?: BlurredBackdropCache.getOrCreate(context, normalized)
            loaded = LoadedArtwork(normalized, image)
        }
    }
    // Keep the previous blurred image while a new track is decoded, avoiding a backdrop flash.
    return if (normalized == null) null else cached ?: loaded.bitmap
}

/** Three fast clamped box passes approximate a wide Gaussian at thumbnail resolution. */
private fun preblurBackdrop(original: Bitmap): Bitmap {
    val scale = (BACKDROP_TARGET_PIXELS.toFloat() / max(original.width, original.height))
        .coerceAtMost(1f)
    val width = (original.width * scale).roundToInt().coerceAtLeast(1)
    val height = (original.height * scale).roundToInt().coerceAtLeast(1)
    val source = Bitmap.createScaledBitmap(original, width, height, true)
    val pixels = IntArray(width * height)
    val scratch = IntArray(pixels.size)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
        blurHorizontal(pixels, scratch, width, height, BACKDROP_BLUR_RADIUS_PX)
        blurVertical(scratch, pixels, width, height, BACKDROP_BLUR_RADIUS_PX)
    }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    if (source !== original) source.recycle()
    return output
}

private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val divisor = radius * 2 + 1
    for (y in 0 until height) {
        val start = y * width
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (offset in -radius..radius) {
            val pixel = src[start + offset.coerceIn(0, width - 1)]
            a += pixel ushr 24
            r += (pixel ushr 16) and 255
            g += (pixel ushr 8) and 255
            b += pixel and 255
        }
        for (x in 0 until width) {
            dst[start + x] = ((a / divisor) shl 24) or
                ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
            val leaving = src[start + (x - radius).coerceIn(0, width - 1)]
            val entering = src[start + (x + radius + 1).coerceIn(0, width - 1)]
            a += (entering ushr 24) - (leaving ushr 24)
            r += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
            g += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
            b += (entering and 255) - (leaving and 255)
        }
    }
}

private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val divisor = radius * 2 + 1
    for (x in 0 until width) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (offset in -radius..radius) {
            val pixel = src[offset.coerceIn(0, height - 1) * width + x]
            a += pixel ushr 24
            r += (pixel ushr 16) and 255
            g += (pixel ushr 8) and 255
            b += pixel and 255
        }
        for (y in 0 until height) {
            dst[y * width + x] = ((a / divisor) shl 24) or
                ((r / divisor) shl 16) or ((g / divisor) shl 8) or (b / divisor)
            val leaving = src[(y - radius).coerceIn(0, height - 1) * width + x]
            val entering = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            a += (entering ushr 24) - (leaving ushr 24)
            r += ((entering ushr 16) and 255) - ((leaving ushr 16) and 255)
            g += ((entering ushr 8) and 255) - ((leaving ushr 8) and 255)
            b += (entering and 255) - (leaving and 255)
        }
    }
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

/**
 * Returns a high-contrast accent sampled locally from artwork. No new bitmap request/network call:
 * the small image uses the same LRU decoder as the rest of the player.
 */
@Composable
fun rememberArtworkAccentColor(artworkRef: String?): Color? {
    val bitmap = rememberArtworkBitmap(artworkRef, targetPixels = 96)
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var color by remember(artworkRef, isLight) { mutableStateOf<Color?>(null) }
    LaunchedEffect(bitmap, artworkRef, isLight) {
        color = if (bitmap == null || artworkRef == null) null else withContext(Dispatchers.Default) {
            extractArtworkAccent(bitmap.asAndroidBitmap(), isLight)
        }
    }
    return color
}

private fun extractArtworkAccent(bitmap: android.graphics.Bitmap, lightTheme: Boolean): Color? {
    if (bitmap.width == 0 || bitmap.height == 0) return null
    // Prefer chromatic, opaque pixels; discard dark borders and near-white highlights.
    val votes = FloatArray(12)
    val saturation = FloatArray(12)
    val hsv = FloatArray(3)
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    for (y in stepY / 2 until bitmap.height step stepY) {
        for (x in stepX / 2 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 180) continue
            AndroidColor.colorToHSV(pixel, hsv)
            if (hsv[1] < 0.23f || hsv[2] < 0.18f || hsv[2] > 0.97f) continue
            val bin = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            val weight = hsv[1] * (0.4f + hsv[2])
            votes[bin] += weight
            saturation[bin] += hsv[1] * weight
        }
    }
    val best = votes.indices.maxByOrNull { votes[it] } ?: return null
    if (votes[best] < 0.5f) return null
    val s = (saturation[best] / votes[best]).coerceIn(0.38f, 0.78f)
    val v = if (lightTheme) 0.57f else 0.89f
    return Color(AndroidColor.HSVToColor(floatArrayOf(best * 30f + 15f, s, v)))
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
    cornerRadius: Dp = 28.dp,
) {
    val bitmap = rememberArtworkBitmap(
        artworkRef = artworkRef,
        targetPixels = PLAYER_ARTWORK_TARGET_PIXELS,
        retainPreviousWhileLoading = true,
    )
    val shape = RoundedCornerShape(cornerRadius)
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
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
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

/** Pre-blurred thumbnail stretched behind the player; no per-frame RenderEffect needed. */
@Composable
fun TrackArtworkBackdrop(
    artworkRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberBlurredBackdrop(artworkRef)

    val palette = MaterialTheme.colorScheme
    val lightTheme = palette.background.luminance() > 0.5f
    val wash = if (lightTheme) palette.surface else Color.Black
    Box(modifier = modifier.background(palette.background)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.92f,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to wash.copy(alpha = if (lightTheme) 0.86f else 0.26f),
                                0.34f to wash.copy(alpha = if (lightTheme) 0.82f else 0.18f),
                                0.58f to wash.copy(alpha = if (lightTheme) 0.86f else 0.34f),
                                0.78f to wash.copy(alpha = if (lightTheme) 0.91f else 0.58f),
                                1.00f to wash.copy(alpha = if (lightTheme) 0.94f else 0.82f),
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
                                palette.surfaceVariant,
                                palette.background,
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
                                0.00f to wash.copy(alpha = if (lightTheme) 0.64f else 0.18f),
                                0.50f to wash.copy(alpha = if (lightTheme) 0.72f else 0.30f),
                                1.00f to wash.copy(alpha = if (lightTheme) 0.94f else 0.82f),
                            ),
                        ),
                    ),
            )
        }
    }
}

private val ARTWORK_SIZE_BUCKETS = intArrayOf(128, 256, 512, 1024, 1536, 2048)
private const val PLAYER_ARTWORK_TARGET_PIXELS = 2048
private const val BACKDROP_TARGET_PIXELS = 384
private const val BACKDROP_BLUR_RADIUS_PX = 24
