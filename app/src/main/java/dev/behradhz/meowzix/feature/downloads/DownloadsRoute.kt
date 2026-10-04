package dev.behradhz.meowzix.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.ManagedStorageUsage
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.TrackArtwork
import java.util.Locale

@Composable
fun DownloadsRoute(
    viewModel: DownloadsViewModel = hiltViewModel(),
    storageViewModel: StoragePolicyViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val settings by viewModel.networkSettings.collectAsStateWithLifecycle()
    val selectedChats by viewModel.selectedTelegramChats.collectAsStateWithLifecycle()
    val chatDownloadProgress by viewModel.chatDownloadProgress.collectAsStateWithLifecycle()
    val storageUsage by storageViewModel.usage.collectAsStateWithLifecycle()
    val storageSettings by storageViewModel.settings.collectAsStateWithLifecycle()
    val active = rows.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val rest = rows.filterNot { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 182.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Offline", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { SettingToggle("Offline mode", settings.offlineMode, viewModel::setOfflineMode) }
        item { SettingToggle("Wi-Fi only downloads", settings.wifiOnlyDownloads, viewModel::setWifiOnly) }
        item { SettingToggle("Prefetch next track", settings.prefetchEnabled, viewModel::setPrefetch) }
        item { SettingToggle("Prefetch on metered networks", settings.prefetchOnMetered, viewModel::setPrefetchOnMetered, enabled = settings.prefetchEnabled && !settings.wifiOnlyDownloads) }
        item {
            StoragePolicyCard(
                usage = storageUsage,
                selectedBudget = storageSettings.temporaryCacheBudgetBytes,
                onBudgetSelected = storageViewModel::setBudget,
                onClearTemporary = storageViewModel::clearTemporaryCache,
            )
        }
        if (selectedChats.isNotEmpty()) {
            item { SectionTitle("Telegram sources") }
            items(selectedChats, key = { it.chatId }) { chat ->
                TelegramDownloadSource(
                    chat = chat,
                    progress = chatDownloadProgress[chat.chatId],
                    onToggleDownloadAll = { viewModel.toggleDownloadAll(chat.chatId) },
                )
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("Downloading now") }
            items(active, key = { it.trackId }) { row ->
                DownloadRowCard(
                    row = row,
                    onPause = { viewModel.pause(row.trackId) },
                    onResume = { viewModel.resume(row.trackId) },
                    onCancel = { viewModel.cancel(row.trackId) },
                    onRetry = { viewModel.retry(row.trackId) },
                    onRemove = { viewModel.remove(row.trackId) },
                )
            }
        }
        item { SectionTitle("Download library") }
        if (rest.isEmpty() && active.isEmpty()) {
            item { Text("Use the download icon on a track cover, or download a whole Telegram source above.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)) }
        } else {
            items(rest, key = { it.trackId }) { row ->
                DownloadRowCard(
                    row = row,
                    onPause = { viewModel.pause(row.trackId) },
                    onResume = { viewModel.resume(row.trackId) },
                    onCancel = { viewModel.cancel(row.trackId) },
                    onRetry = { viewModel.retry(row.trackId) },
                    onRemove = { viewModel.remove(row.trackId) },
                )
            }
        }
    }
}

@Composable
private fun StoragePolicyCard(
    usage: ManagedStorageUsage?,
    selectedBudget: Long,
    onBudgetSelected: (Long) -> Unit,
    onClearTemporary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Managed storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (usage == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "Temporary ${formatBytes(usage.temporaryPlaybackBytes)} · Pinned ${formatBytes(usage.pinnedOfflineBytes)} · Other ${formatBytes(usage.otherManagedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Total ${formatBytes(usage.totalManagedBytes)}${if (usage.protectedBytes > 0L) " · ${formatBytes(usage.protectedBytes)} in use" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                if (usage.lowSpace) {
                    Text(
                        "Storage is low. Nonessential cache work is suspended until space recovers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text("Temporary cache budget", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CACHE_BUDGET_PRESETS.forEach { bytes ->
                    if (selectedBudget == bytes) {
                        Button(onClick = { onBudgetSelected(bytes) }) { Text(formatBudget(bytes)) }
                    } else {
                        OutlinedButton(onClick = { onBudgetSelected(bytes) }) { Text(formatBudget(bytes)) }
                    }
                }
            }
            OutlinedButton(onClick = onClearTemporary) { Text("Clear temporary cache") }
            Text(
                "Pinned offline copies and your own MediaStore audio are never removed by Clear temporary cache.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun TelegramDownloadSource(
    chat: TelegramChatSummary,
    progress: ChatDownloadProgress?,
    onToggleDownloadAll: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(chat.profilePhotoRef, chat.title, size = 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = progress?.displayText() ?: "Selected Telegram source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                Surface(
                    onClick = onToggleDownloadAll,
                    modifier = Modifier.size(62.dp),
                    shape = RoundedCornerShape(31.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.size(50.dp),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            text = if (progress.usesBytes) "${(progress.fraction * 100).toInt()}%" else progress.completedTracks.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Button(onClick = onToggleDownloadAll, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp)); Text("All")
                }
            }
        }
    }
}

@Composable
private fun DownloadRowCard(
    row: DownloadRow,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(row.artworkRef, row.title, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.status.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                }
                when (row.status) {
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.QUEUED,
                    -> OutlinedButton(onClick = onPause) { Text("Pause") }
                    DownloadStatus.PAUSED -> Button(onClick = onResume) { Text("Resume") }
                    DownloadStatus.FAILED,
                    DownloadStatus.CANCELED,
                    -> Button(onClick = onRetry) { Text("Retry") }
                    DownloadStatus.COMPLETED -> OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }
            if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.size(8.dp))
                if (row.progress != null) LinearProgressIndicator(progress = { row.progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (row.status == DownloadStatus.PAUSED) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
            row.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label, modifier = Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
}
private val DownloadStatus.label: String get() = when (this) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETED -> "Available offline"
    DownloadStatus.FAILED -> "Download failed"
    DownloadStatus.CANCELED -> "Canceled"
}

private fun ChatDownloadProgress.displayText(): String = if (usesBytes) {
    "${formatBytes(downloadedBytes ?: 0L)} of ${formatBytes(totalBytes ?: 0L)} · tap the ring to stop"
} else {
    "Downloading $completedTracks of $totalTracks · tap the ring to stop"
}

private fun formatBytes(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 1024.0) {
        String.format(Locale.US, "%.1f GB", mib / 1024.0)
    } else {
        String.format(Locale.US, "%.0f MB", mib)
    }
}

private fun formatBudget(bytes: Long): String = when (bytes) {
    256L * 1024L * 1024L -> "256 MB"
    512L * 1024L * 1024L -> "512 MB"
    1024L * 1024L * 1024L -> "1 GB"
    else -> formatBytes(bytes)
}

private val CACHE_BUDGET_PRESETS = listOf(
    256L * 1024L * 1024L,
    512L * 1024L * 1024L,
    1024L * 1024L * 1024L,
)
