package dev.behradhz.meowzix.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared swipeable artist/album row used by Library and global search. */
@Composable
internal fun SwipeableCollectionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    /** Clicking the card always opens details; this control selects the entire collection. */
    onSelect: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    partiallySelected: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var offsetX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val threshold = with(density) { 75.dp.toPx() }
    val visualOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (dragging) snap() else tween(190),
        label = "aggregate-library-swipe",
    )
    val swipeRight = visualOffset > 0f
    Box(modifier = Modifier.fillMaxWidth()) {
        if (!selectionMode && onPlayNext != null && onAddToQueue != null && kotlin.math.abs(visualOffset) > 0.5f) {
            Surface(
                modifier = Modifier.matchParentSize().drawWithContent {
                    val edge = visualOffset.coerceIn(-size.width, size.width)
                    if (edge > 0f) clipRect(left = 0f, right = edge) {
                        this@drawWithContent.drawContent()
                    } else if (edge < 0f) clipRect(left = size.width + edge, right = size.width) {
                        this@drawWithContent.drawContent()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = if (swipeRight) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = if (swipeRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipeRight) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (swipeRight) "Play next" else "Add to queue",
                        fontWeight = FontWeight.SemiBold)
                    if (!swipeRight) {
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { translationX = visualOffset }
                .pointerInput(title, onPlayNext, onAddToQueue, selectionMode) {
                    if (!selectionMode && onPlayNext != null && onAddToQueue != null) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                val old = kotlin.math.abs(offsetX)
                                offsetX = (offsetX + delta).coerceIn(-threshold * 3, threshold * 3)
                                if (old < threshold && kotlin.math.abs(offsetX) >= threshold)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                val released = offsetX
                                dragging = false
                                offsetX = 0f
                                when {
                                    released >= threshold -> onPlayNext()
                                    released <= -threshold -> onAddToQueue()
                                }
                            },
                            onDragCancel = { dragging = false; offsetX = 0f },
                        )
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = Color(0x4D252329),
            contentColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.70f))
                }
                if (onSelect != null) {
                    TriStateCheckbox(
                        state = when {
                            selected -> ToggleableState.On
                            partiallySelected -> ToggleableState.Indeterminate
                            else -> ToggleableState.Off
                        },
                        onClick = onSelect,
                        modifier = Modifier.size(48.dp),
                    )
                } else if (onPlayNext != null && onAddToQueue != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Queue actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                menuOpen = false; onPlayNext()
                            })
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                                menuOpen = false; onAddToQueue()
                            })
                        }
                    }
                }
            }
        }
    }
}

