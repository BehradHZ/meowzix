package dev.behradhz.meowzix.feature.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.backup.BackupPreview
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UnifiedSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: UnifiedSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeHistoryInBackup by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<ByteArray, BackupPreview>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = viewModel.exportBackup(includeHistoryInBackup)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Unable to open backup destination")
                }
            }.onSuccess { Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "Backup failed", Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(32 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= dev.behradhz.meowzix.data.backup.BackupFormat.MAX_BYTES) { "Backup is too large" }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    } ?: error("Unable to read backup")
                }
                bytes to viewModel.previewBackup(bytes)
            }.onSuccess { pendingRestore = it }
                .onFailure { Toast.makeText(context, it.message ?: "Invalid backup", Toast.LENGTH_LONG).show() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item { SectionTitle("Appearance & accessibility") }
            item { ChoiceRow("Theme", state.appearance.theme.name.lowercase().replaceFirstChar(Char::uppercase), ThemePreference.entries.map { it.name }) { viewModel.setTheme(ThemePreference.valueOf(it)) } }
            item { SliderRow("Lyrics text size", "${state.appearance.lyricsTextScalePercent}%", state.appearance.lyricsTextScalePercent.toFloat(), 80f..160f, 7) { viewModel.setLyricsScale(it.toInt()) } }
            item { ToggleRow("Reduced motion", "Use shorter/minimal lyric and UI motion", state.appearance.reduceMotion, viewModel::setReduceMotion) }

            item { SectionTitle("Playback") }
            item { ChoiceRow("Default playback mode", state.playback.defaultMode.name, PlaybackMode.entries.map { it.name }) { viewModel.setDefaultMode(PlaybackMode.valueOf(it)) } }
            item { ChoiceRow("Default repeat", state.playback.defaultRepeat.name, RepeatMode.entries.map { it.name }) { viewModel.setDefaultRepeat(RepeatMode.valueOf(it)) } }
            item { ToggleRow("Resume playback state", "Restore the previous queue and position", state.playback.resumeOnLaunch, viewModel::setResume) }

            item { SectionTitle("Recommendations") }
            item { ToggleRow("Smart recommendations", "Enable learned recommendation surfaces", state.recommendations.smartRecommendationsEnabled, viewModel::setSmart) }
            item { SliderRow("Exploration", "${state.recommendations.explorationPercent}%", state.recommendations.explorationPercent.toFloat(), 0f..50f, 9) { viewModel.setExploration(it.toInt()) } }
            item { ToggleRow("Local audio analysis", "Analyze readable local audio only", state.recommendations.audioAnalysisEnabled, viewModel::setAudioAnalysis) }
            item { ToggleRow("Diagnostics", "Show local model and evaluation details", state.recommendations.diagnosticsVisible, viewModel::setDiagnostics) }
            item { TextButton(onClick = viewModel::clearRecommendationFeedback, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Clear recommendation feedback") } }

            item { SectionTitle("History & privacy") }
            item { ToggleRow("Listening history", "Disabling this also stops new learning-event capture", state.network.listeningHistoryEnabled, viewModel::setHistory) }
            item { DestructiveRow("Clear listening history", viewModel::clearHistory) }
            item { DestructiveRow("Reset personalization", viewModel::resetPersonalization) }

            item { SectionTitle("Downloads & storage") }
            item { ToggleRow("Wi-Fi only downloads", null, state.network.wifiOnlyDownloads, viewModel::setWifiOnly) }
            item { ToggleRow("Prefetch", "Prepare likely next tracks when policy allows", state.network.prefetchEnabled, viewModel::setPrefetch) }
            item { SliderRow("Temporary cache budget", formatBytes(state.storage.temporaryCacheBudgetBytes), state.storage.temporaryCacheBudgetBytes / MIB.toFloat(), 64f..2048f, 30) { viewModel.setCacheBudget(it.toLong() * MIB) } }

            item { SectionTitle("Backup") }
            item { ToggleRow("Include listening history", "Off by default; trained model weights are never exported", includeHistoryInBackup) { includeHistoryInBackup = it } }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { exportLauncher.launch("meowzix-backup.mzx") }, modifier = Modifier.weight(1f)) { Text("Export") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/*", "text/*")) }, modifier = Modifier.weight(1f)) { Text("Import") }
                }
            }
        }
    }

    pendingRestore?.let { (bytes, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${preview.recordCounts.values.sum()} records")
                    Text("${preview.unresolvedTrackReferences} track references cannot currently be matched")
                    Text(if (preview.includeHistory) "Listening history is included" else "Listening history is not included")
                    Text("Restore merges state; it does not replace your library or Telegram login.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        runCatching { viewModel.restoreBackup(bytes) }
                            .onSuccess { Toast.makeText(context, "Restored ${it.restoredRecords} records; ${it.unresolvedReferences} unresolved", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, it.message ?: "Restore failed", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable private fun SliderRow(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, steps = steps)
    }
}

@Composable private fun ChoiceRow(title: String, current: String, options: List<String>, onChoice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(current) }
        if (expanded) options.forEach { option ->
            TextButton(onClick = { expanded = false; onChoice(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) }
        }
    }
}

@Composable private fun DestructiveRow(title: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp)) { Text(title, color = MaterialTheme.colorScheme.error) }
}

private fun formatBytes(bytes: Long): String = if (bytes >= 1024L * MIB) "%.1f GiB".format(bytes / (1024f * MIB)) else "${bytes / MIB} MiB"
private const val MIB = 1024L * 1024L
