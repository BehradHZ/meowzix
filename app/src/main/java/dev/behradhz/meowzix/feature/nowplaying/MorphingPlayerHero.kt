package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.QueueState
import kotlin.math.min

private val HeroPrimary = Color(0xFFF7F3EF)
private val HeroSecondary = Color(0xFFCFC7C0)

/**
 * One artwork node and one identity node are retained for the whole transition. Their bounds,
 * positions and typography are interpolated; the compact lyric preview occupies the space directly
 * below the same artwork and fades out as the hero becomes the expanded horizontal header.
 */
@Composable
fun MorphingPlayerHero(
    state: PlaybackState,
    queueState: QueueState,
    expanded: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    favorite: Boolean,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit,
    compactLyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 500f),
        label = "lyrics-hero-morph",
    )

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Box {
                ArtworkGestureZoneForMorph(
                    state = state,
                    queueState = queueState,
                    artworkRef = track.artworkRef,
                    artworkDescription = "${track.title} cover art",
                    onBack = onBack,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onBackdropTransition = onBackdropTransition,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.graphicsLayer { alpha = 1f - progress }) { compactLyrics() }
            MorphingIdentity(
                title = track.title,
                artist = track.artist ?: "Unknown artist",
                favorite = favorite,
                progress = progress,
                onToggleFavorite = onToggleFavorite,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: width
        val compactArt = 64.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val previewHeight = 118.dp.roundToPx()
        val identityReserve = 66.dp.roundToPx()
        val collapsedArt = min(
            width,
            (height - previewHeight - identityReserve - 8.dp.roundToPx()).coerceAtLeast(compactArt),
        )
        val artSize = lerpInt(collapsedArt, compactArt, progress).coerceAtLeast(1)
        val expandedIdentityWidth = (width - compactArt - gap).coerceAtLeast(1)
        val identityWidth = lerpInt(width, expandedIdentityWidth, progress).coerceAtLeast(1)

        val artwork = measurables[0].measure(Constraints.fixed(artSize, artSize))
        val preview = measurables[1].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = previewHeight,
                maxHeight = previewHeight,
            ),
        )
        val identity = measurables[2].measure(
            Constraints(
                minWidth = identityWidth,
                maxWidth = identityWidth,
                minHeight = 0,
                maxHeight = height,
            ),
        )

        val collapsedArtX = (width - artSize) / 2
        val artX = lerpInt(collapsedArtX, 0, progress)
        val artY = lerpInt(0, 4.dp.roundToPx(), progress)
        val previewY = collapsedArt + 4.dp.roundToPx()
        val collapsedIdentityY = (collapsedArt + previewHeight + 8.dp.roundToPx()).coerceAtMost(height)
        val identityX = lerpInt(0, compactArt + gap, progress)
        val identityY = lerpInt(collapsedIdentityY, 2.dp.roundToPx(), progress)

        layout(width, height) {
            artwork.placeRelative(artX, artY)
            preview.placeRelative(0, previewY)
            identity.placeRelative(identityX, identityY)
        }
    }
}

@Composable
private fun MorphingIdentity(
    title: String,
    artist: String,
    favorite: Boolean,
    progress: Float,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = title,
                fontSize = (24f - 8f * progress).sp,
                lineHeight = (29f - 8f * progress).sp,
                fontWeight = FontWeight.Bold,
                color = HeroPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                fontSize = (16f - 3f * progress).sp,
                lineHeight = (21f - 3f * progress).sp,
                color = HeroSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size((48f - 8f * progress).dp)) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) MaterialTheme.colorScheme.primary else HeroPrimary,
                modifier = Modifier.size((28f - 5f * progress).dp),
            )
        }
    }
}

@Composable
private fun ArtworkGestureZoneForMorph(
    state: PlaybackState,
    queueState: QueueState,
    artworkRef: String?,
    artworkDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit,
    modifier: Modifier = Modifier,
) {
    NowPlayingArtworkPager(
        state = state,
        queueState = queueState,
        fallbackArtworkRef = artworkRef,
        fallbackArtworkDescription = artworkDescription,
        onBack = onBack,
        onPrevious = onPrevious,
        onNext = onNext,
        onBackdropTransition = onBackdropTransition,
        modifier = modifier,
    )
}

private fun lerpInt(start: Int, stop: Int, fraction: Float): Int =
    (start + (stop - start) * fraction.coerceIn(0f, 1f)).toInt()
