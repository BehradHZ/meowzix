from pathlib import Path
import re


def require_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} changed unexpectedly")
    return text.replace(old, new, 1)


# Queue: pin header, remove back affordance, and let the drag handle own long-press.
p = Path("app/src/main/java/dev/behradhz/meowzix/feature/queue/QueueRoute.kt")
s = p.read_text()
s = s.replace("import androidx.compose.foundation.combinedClickable\n", "import androidx.compose.foundation.clickable\n")
s = s.replace("import androidx.compose.material.icons.rounded.ArrowBack\n", "")
s = require_replace(
    s,
    "fun QueueRoute(\n    onBack: () -> Unit,\n    viewModel: QueueViewModel = hiltViewModel(),\n)",
    "fun QueueRoute(\n    viewModel: QueueViewModel = hiltViewModel(),\n)",
    "QueueRoute signature",
)
s = s.replace("        onBack = onBack,\n", "", 1)
s = s.replace("    onBack: () -> Unit,\n", "", 1)
s = require_replace(
    s,
    "@Composable\nprivate fun QueueScreen(",
    "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun QueueScreen(",
    "QueueScreen annotation",
)
lazy = s.index("    LazyColumn(")
hs = s.index("        item {\n            Row(", lazy)
he = s.index("\n\n        if (displayItems.isEmpty())", hs)
header = '''        stickyHeader {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.items.isEmpty()) "Nothing queued" else "${state.items.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                        )
                    }
                    if (state.items.size > 1) {
                        val shuffleEnabled = state.playbackMode == PlaybackMode.PURE_SHUFFLE
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = if (shuffleEnabled) "Disable shuffle" else "Shuffle queue",
                                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.items.isNotEmpty()) {
                        Button(onClick = onClear, shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }'''
s = s[:hs] + header + s[he:]
s = require_replace(
    s,
    ".fillMaxWidth().combinedClickable(onClick = onPlay, onLongClick = { menuExpanded = true })",
    ".fillMaxWidth().clickable(onClick = onPlay)",
    "queue row long click",
)
p.write_text(s)

# Queue is top-level; caller no longer supplies a back callback.
p = Path("app/src/main/java/dev/behradhz/meowzix/navigation/MeowzixApp.kt")
s = p.read_text()
s, n = re.subn(
    r"QueueRoute\(\s*onBack\s*=\s*navController::popBackStack\s*\)",
    "QueueRoute()",
    s,
)
if n == 0:
    raise SystemExit("QueueRoute call site changed unexpectedly")
p.write_text(s)

# Queue reorder: reconcile Media3 items in-place so the active decoder is never reset.
p = Path("app/src/main/java/dev/behradhz/meowzix/playback/AndroidPlaybackController.kt")
s = p.read_text()
old = '''    override fun move(fromIndex: Int, toIndex: Int) {
        withController { connected ->
            if (progressiveQueue.snapshot() != null) {
                if (progressiveQueue.move(fromIndex, toIndex)) rebuildProgressiveWindow(connected)
            } else if (fromIndex in 0 until connected.mediaItemCount && toIndex in 0 until connected.mediaItemCount) {
                connected.moveMediaItem(fromIndex, toIndex)
            }
        }
    }
'''
new = '''    override fun move(fromIndex: Int, toIndex: Int) {
        withController { connected ->
            if (progressiveQueue.snapshot() != null) {
                if (progressiveQueue.move(fromIndex, toIndex)) syncProgressiveWindowInPlace(connected)
            } else if (fromIndex in 0 until connected.mediaItemCount && toIndex in 0 until connected.mediaItemCount) {
                connected.moveMediaItem(fromIndex, toIndex)
            }
        }
    }
'''
s = require_replace(s, old, new, "playback move")
anchor = "    private fun rebuildProgressiveWindow(\n"
helper = '''    /** Reconciles a reordered progressive window without resetting the active MediaItem. */
    private fun syncProgressiveWindowInPlace(connected: MediaController) {
        val currentMediaId = connected.currentMediaItem?.mediaId ?: return
        if (!progressiveQueue.updateCurrent(currentMediaId)) return
        val plan = progressiveQueue.resetWindow()
        val snapshot = progressiveQueue.snapshot() ?: return
        plan.tracks.forEachIndexed { targetIndex, track ->
            val desiredId = track.id.toString()
            if (targetIndex < connected.mediaItemCount && connected.getMediaItemAt(targetIndex).mediaId == desiredId) {
                return@forEachIndexed
            }
            val existingIndex = (targetIndex + 1 until connected.mediaItemCount)
                .firstOrNull { index -> connected.getMediaItemAt(index).mediaId == desiredId }
            if (existingIndex != null) {
                connected.moveMediaItem(existingIndex, targetIndex)
            } else {
                connected.addMediaItem(targetIndex, track.toMediaItem(snapshot.playbackMode, snapshot.repeatMode))
            }
        }
        while (connected.mediaItemCount > plan.tracks.size) {
            connected.removeMediaItem(connected.mediaItemCount - 1)
        }
        connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
        updateState(connected)
    }

'''
if anchor not in s:
    raise SystemExit("progressive helper anchor missing")
s = s.replace(anchor, helper + anchor, 1)
p.write_text(s)

# Now Playing: restore real FFT spectrum and mask Media3's one-frame seek lag.
p = Path("app/src/main/java/dev/behradhz/meowzix/feature/nowplaying/NowPlayingRoute.kt")
s = p.read_text()
additions = [
    ("package dev.behradhz.meowzix.feature.nowplaying\n", "package dev.behradhz.meowzix.feature.nowplaying\n\nimport android.Manifest\nimport android.content.pm.PackageManager\n"),
    ("import androidx.compose.foundation.layout.Arrangement\n", "import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.compose.foundation.layout.Arrangement\n"),
    ("import androidx.compose.runtime.Composable\n", "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n"),
    ("import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalContext\n"),
    ("import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel\n", "import androidx.core.content.ContextCompat\nimport androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel\n"),
    ("import dev.chrisbanes.haze.rememberHazeState\n", "import dev.chrisbanes.haze.rememberHazeState\nimport kotlin.math.abs\nimport kotlinx.coroutines.delay\n"),
]
for a, b in additions:
    extra_lines = [line for line in b.splitlines() if line and line not in a]
    if extra_lines and extra_lines[0] not in s:
        s = require_replace(s, a, b, f"import {extra_lines[0]}")
anchor = "    val queueState by viewModel.queueState.collectAsStateWithLifecycle()\n"
if "    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()\n" not in s:
    s = require_replace(s, anchor, anchor + "    val spectrum by viewModel.spectrum.collectAsStateWithLifecycle()\n", "spectrum state")
anchor = "    val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()\n"
if "visualizerPermissionLauncher" not in s:
    block = '''    val context = LocalContext.current
    val visualizerPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) viewModel.refreshVisualizer() },
    )
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.refreshVisualizer()
        } else {
            visualizerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
'''
    s = require_replace(s, anchor, anchor + block, "visualizer permission")
if "        liveBands = spectrum.bands,\n" not in s:
    s = require_replace(s, "        queueState = queueState,\n", "        queueState = queueState,\n        liveBands = spectrum.bands,\n", "live bands call")
if "    liveBands: FloatArray,\n" not in s:
    s = require_replace(s, "    queueState: QueueState,\n", "    queueState: QueueState,\n    liveBands: FloatArray,\n", "live bands parameter")
old = '''    var pendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
    var backdropTransition by remember { mutableStateOf<ArtworkBackdropTransition?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = pendingSeek?.toLong() ?: state.positionMs.coerceIn(0L, duration)
'''
new = '''    var pendingSeek by remember(track.id) { mutableStateOf<Float?>(null) }
    var settlingSeekTarget by remember(track.id) { mutableStateOf<Long?>(null) }
    var backdropTransition by remember { mutableStateOf<ArtworkBackdropTransition?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val shownPosition = pendingSeek?.toLong() ?: settlingSeekTarget ?: state.positionMs.coerceIn(0L, duration)
    LaunchedEffect(state.positionMs, settlingSeekTarget) {
        val target = settlingSeekTarget ?: return@LaunchedEffect
        if (abs(state.positionMs - target) <= 900L) settlingSeekTarget = null
    }
    LaunchedEffect(settlingSeekTarget) {
        val target = settlingSeekTarget ?: return@LaunchedEffect
        delay(650L)
        if (settlingSeekTarget == target) settlingSeekTarget = null
    }
'''
s = require_replace(s, old, new, "seek settling state")
old = '''                onValueChangeFinished = {
                    pendingSeek?.let { onSeek(it.toLong()) }
                    pendingSeek = null
                },
'''
new = '''                onValueChangeFinished = {
                    pendingSeek?.let { value ->
                        val target = value.toLong()
                        settlingSeekTarget = target
                        onSeek(target)
                    }
                    pendingSeek = null
                },
'''
s = require_replace(s, old, new, "seek finish")
if "                liveBands = liveBands,\n" not in s:
    s = require_replace(s, "                trackKey = track.id.toString(),\n", "                trackKey = track.id.toString(),\n                liveBands = liveBands,\n", "waveform live bands")
p.write_text(s)

# Equalizer: remove procedural sine movement; actual FFT controls live heights.
p = Path("app/src/main/java/dev/behradhz/meowzix/ui/components/SyntheticWaveformSeekBar.kt")
s = p.read_text()
s = s.replace("import kotlin.math.PI\n", "").replace("import kotlin.math.sin\n", "")
s = require_replace(s, "    trackKey: String,\n    value: Float,", "    trackKey: String,\n    liveBands: FloatArray,\n    value: Float,", "waveform signature")
s = require_replace(
    s,
    "    val waveform = remember(trackKey, resolvedBarCount) {\n        buildSyntheticWaveform(trackKey, resolvedBarCount)\n    }\n",
    "    val waveform = remember(trackKey, resolvedBarCount) { buildSyntheticWaveform(trackKey, resolvedBarCount) }\n    val liveProfile = remember(liveBands, resolvedBarCount) { expandLiveSpectrum(liveBands, resolvedBarCount) }\n    val hasLiveSpectrum = remember(liveBands) { liveBands.any { it > 0.001f } }\n",
    "live spectrum setup",
)
old = '''    val interactionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "waveformSeekInteraction",
    )

    val phase = remember { Animatable(0f) }
    val shouldAnimateBars = enabled && isPlaying && !isLoading && !isInteracting
    LaunchedEffect(shouldAnimateBars, animationDurationMs) {
        if (!shouldAnimateBars) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        val fullCycle = (2.0 * PI).toFloat()
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = fullCycle,
                animationSpec = tween(
                    durationMillis = animationDurationMs.coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
        }
    }
'''
new = '''    val scrubBlend by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = if (isInteracting) 280 else 360, easing = FastOutSlowInEasing),
        label = "waveformScrubBlend",
    )
    val pointerInteraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "waveformPointerInteraction",
    )
'''
s = require_replace(s, old, new, "procedural animation block")
old = '''                    val decay = (distanceFromPointer / decayWindow).coerceIn(0f, 1f)
                    val activity = if (shouldAnimateBars) {
                        val oscillator = (
                            sin(
                                phase.value +
                                    index * 0.83f +
                                    baseHeight * PI.toFloat(),
                            ) + 1f
                        ) / 2f
                        0.56f + 0.44f * oscillator
                    } else {
                        0.10f
                    }
                    halfHeight = (baseHeight * maxHalfHeight * activity * decay)
                        .coerceAtLeast(minimumHalfHeightPx)
'''
new = '''                    val linearDecay = (distanceFromPointer / decayWindow).coerceIn(0f, 1f)
                    val decay = smoothStep(linearDecay)
                    val liveHeight = if (isPlaying && hasLiveSpectrum) liveProfile[index].coerceIn(0.08f, 1f) else baseHeight
                    val blendedHeight = lerpFloat(liveHeight, baseHeight, scrubBlend)
                    halfHeight = (blendedHeight * maxHalfHeight * decay).coerceAtLeast(minimumHalfHeightPx)
'''
s = require_replace(s, old, new, "active equalizer heights")
s = s.replace("                    interactionFraction,\n", "                    pointerInteraction,\n")
s = s.replace("    animationDurationMs: Int = 820,\n", "")
s = s.replace(
    "        val jitter = deterministicNoise(seed, index) * 0.10f\n        output[index] = (interpolated + jitter).coerceIn(0.18f, 0.96f)\n",
    "        output[index] = interpolated.coerceIn(0.18f, 0.96f)\n",
)
s, n = re.subn(
    r"\nprivate fun deterministicNoise\([\s\S]*?\n}\n\nprivate fun lerpFloat",
    "\nprivate fun expandLiveSpectrum(bands: FloatArray, barCount: Int): FloatArray {\n    if (bands.isEmpty()) return FloatArray(barCount)\n    return FloatArray(barCount) { index -> bands[index % bands.size].coerceIn(0f, 1f) }\n}\n\nprivate fun smoothStep(value: Float): Float {\n    val x = value.coerceIn(0f, 1f)\n    return x * x * (3f - 2f * x)\n}\n\nprivate fun lerpFloat",
    s,
    count=1,
)
if n != 1:
    raise SystemExit("deterministic noise helper changed unexpectedly")
p.write_text(s)
