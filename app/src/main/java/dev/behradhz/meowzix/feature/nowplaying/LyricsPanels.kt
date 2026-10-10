package dev.behradhz.meowzix.feature.nowplaying

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowAction
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowMachine
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowMode
import kotlinx.coroutines.delay

@Composable
fun CompactLyricsPreview(
    state: NowPlayingLyricsState,
    onExpand: () -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected?.takeIf { state.hasDisplayableLyrics } ?: return
    Surface(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth().height(118.dp),
        color = Color.Transparent,
    ) {
        when (selected.parsed.contentType) {
            LyricsContentType.TIMED -> AnimatedContent(
                targetState = state.activeLineIndex,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (slideInVertically { it / 3 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 3 } + fadeOut())
                    }
                },
                label = "lyrics-preview-line-change",
                modifier = Modifier.fillMaxSize(),
            ) { activeIndex ->
                val lines = state.effectiveLines
                Column(
                    Modifier.fillMaxSize().lyricsEdgeFade(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PreviewLine(lines.getOrNull(activeIndex - 1)?.text, false)
                    PreviewLine(lines.getOrNull(activeIndex)?.text, true)
                    PreviewLine(lines.getOrNull(if (activeIndex < 0) 0 else activeIndex + 1)?.text, false)
                }
            }
            LyricsContentType.PLAIN -> {
                val lines = selected.parsed.plainLines.take(3)
                Column(
                    Modifier.fillMaxSize().lyricsEdgeFade(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    lines.forEach { PreviewLine(it, false) }
                }
            }
            LyricsContentType.INSTRUMENTAL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Instrumental", color = Color.White.copy(alpha = 0.78f))
            }
            LyricsContentType.INVALID -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lyrics unavailable", color = Color.White.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
private fun PreviewLine(text: String?, current: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .semantics {
                        selected = current
                        if (current) stateDescription = "Current lyric"
                    },
            )
        }
    }
}

@Composable
fun ExpandedLyricsPanel(
    state: NowPlayingLyricsState,
    onLineSeek: (Int) -> Unit,
    onImportLrc: (Uri) -> Unit,
    onPaste: (String) -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onSelectVersion: (java.util.UUID) -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var pasteDialog by remember { mutableStateOf(false) }
    var versionsExpanded by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportLrc)
    }
    val selected = state.selected

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                Text("Import .lrc")
            }
            TextButton(onClick = { pasteDialog = true }) { Text("Paste") }
            Spacer(Modifier.weight(1f))
            if (state.trackLyrics?.versions.orEmpty().size > 1) {
                Box {
                    TextButton(onClick = { versionsExpanded = true }) { Text("Versions") }
                    DropdownMenu(versionsExpanded, onDismissRequest = { versionsExpanded = false }) {
                        state.trackLyrics?.versions.orEmpty().forEach { version ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (version.sourceLabel ?: version.sourceType.name) +
                                            if (version.selected) " · selected" else "",
                                    )
                                },
                                onClick = {
                                    versionsExpanded = false
                                    onSelectVersion(version.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (selected?.parsed?.contentType == LyricsContentType.TIMED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAdjustDelay(-250L) }) { Text("−250 ms") }
                Text(
                    text = "Delay ${if (selected.userDelayMs >= 0) "+" else ""}${selected.userDelayMs} ms",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { onAdjustDelay(250L) }) { Text("+250 ms") }
            }
        }

        when (selected?.parsed?.contentType) {
            null -> EmptyLyricsState(
                onImport = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) },
                onPaste = { pasteDialog = true },
                modifier = Modifier.weight(1f),
            )
            LyricsContentType.TIMED -> TimedLyricsList(
                state = state,
                onLineSeek = onLineSeek,
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
            LyricsContentType.PLAIN -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().lyricsEdgeFade(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(selected.parsed.plainLines.size) { index ->
                    Text(
                        selected.parsed.plainLines[index],
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            LyricsContentType.INSTRUMENTAL -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Instrumental", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            LyricsContentType.INVALID -> EmptyLyricsState(
                onImport = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) },
                onPaste = { pasteDialog = true },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pasteDialog) {
        PasteLyricsDialog(
            onDismiss = { pasteDialog = false },
            onSave = {
                pasteDialog = false
                onPaste(it)
            },
        )
    }
}

@Composable
private fun TimedLyricsList(
    state: NowPlayingLyricsState,
    onLineSeek: (Int) -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val followMachine = remember(state.trackId, state.selected?.id) { LyricsFollowMachine() }
    var mode by remember(state.trackId, state.selected?.id) { mutableStateOf(followMachine.mode) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val lines = state.effectiveLines
    val activeIndex = state.activeLineIndex

    suspend fun settle(index: Int, animated: Boolean = true) {
        if (index !in lines.indices) return
        val viewport = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).coerceAtLeast(1)
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val lineHeight = visible?.size ?: 48
        val anchor = (viewport * 0.40f).toInt()
        val offset = -(anchor - lineHeight / 2)
        programmaticScroll = true
        try {
            if (animated) listState.animateScrollToItem(index, offset) else listState.scrollToItem(index, offset)
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState.isScrollInProgress, programmaticScroll) {
        if (listState.isScrollInProgress && !programmaticScroll) {
            followMachine.onUserInteraction()
            mode = followMachine.mode
        }
    }

    LaunchedEffect(activeIndex, mode) {
        if (activeIndex >= 0 && followMachine.mode == LyricsFollowMode.FOLLOWING) {
            settle(activeIndex, animated = !reduceMotion)
        }
    }

    LaunchedEffect(listState.isScrollInProgress, activeIndex, mode) {
        if (listState.isScrollInProgress || mode == LyricsFollowMode.FOLLOWING || activeIndex !in lines.indices) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex } ?: return@LaunchedEffect
        val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat().coerceAtLeast(1f)
        val anchor = layout.viewportStartOffset + viewportHeight * 0.40f
        val center = item.offset + item.size / 2f
        followMachine.evaluate(center, anchor, item.size.toFloat(), false, false, false, android.os.SystemClock.uptimeMillis())
        mode = followMachine.mode
        if (mode == LyricsFollowMode.REATTACH_ELIGIBLE) {
            delay(250L)
            if (!listState.isScrollInProgress) {
                val latest = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.activeLineIndex }
                if (latest != null) {
                    val latestLayout = listState.layoutInfo
                    val latestViewport = (latestLayout.viewportEndOffset - latestLayout.viewportStartOffset).toFloat().coerceAtLeast(1f)
                    val latestAnchor = latestLayout.viewportStartOffset + latestViewport * 0.40f
                    val action = followMachine.evaluate(
                        latest.offset + latest.size / 2f,
                        latestAnchor,
                        latest.size.toFloat(),
                        false,
                        false,
                        false,
                        android.os.SystemClock.uptimeMillis(),
                    )
                    mode = followMachine.mode
                    if (action == LyricsFollowAction.SETTLE_AND_FOLLOW) {
                        settle(state.activeLineIndex, animated = !reduceMotion)
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().lyricsEdgeFade(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(lines.size, key = { index -> "${lines[index].timeMs}-${lines[index].originalOrder}" }) { index ->
                val current = index == activeIndex
                Text(
                    text = lines[index].text,
                    color = Color.White,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    style = if (current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineSeek(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics {
                            selected = current
                            if (current) stateDescription = "Current lyric"
                        },
                )
            }
        }
        if (mode != LyricsFollowMode.FOLLOWING && activeIndex >= 0) {
            Button(
                onClick = {
                    followMachine.forceFollow()
                    mode = followMachine.mode
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            ) {
                Text("Return to current line")
            }
        }
    }
}

@Composable
private fun EmptyLyricsState(onImport: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No lyrics yet", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = onImport) { Text("Import .lrc") }
            TextButton(onClick = onPaste) { Text("Paste lyrics") }
        }
    }
}

@Composable
private fun PasteLyricsDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste lyrics") },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= dev.behradhz.meowzix.domain.lyrics.LrcParser.MAX_CHARS) text = it },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Box(Modifier.fillMaxSize().padding(12.dp)) {
                            if (text.isBlank()) Text("Lyrics text", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.lyricsEdgeFade(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.18f to Color.Black,
                    0.42f to Color.Black,
                    0.64f to Color.Black,
                    0.84f to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
