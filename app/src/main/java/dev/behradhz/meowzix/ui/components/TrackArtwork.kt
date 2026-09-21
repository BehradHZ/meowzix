package dev.behradhz.meowzix.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberArtworkBitmap(artworkRef: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(artworkRef) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artworkRef) {
        bitmap = if (artworkRef == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(artworkRef))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap
}

@Composable
fun TrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(artworkRef)

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

/**
 * Full-bleed artwork field used by Now Playing. The upper image stays sharp while the lower portion
 * is re-rendered with a heavy blur and dark gradient so controls remain readable, matching the
 * full-screen artwork / matte-bottom treatment used by modern music players.
 */
@Composable
fun TrackArtworkBackdrop(
    artworkRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(artworkRef)

    Box(modifier = modifier.background(Color(0xFF101010))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f)
                    .clipToBounds(),
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.18f
                            scaleY = 1.18f
                        }
                        .blur(42.dp),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.18f),
                                0.34f to Color.Black.copy(alpha = 0.48f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.46f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Black.copy(alpha = 0.52f),
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
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    ),
            )
        }
    }
}
