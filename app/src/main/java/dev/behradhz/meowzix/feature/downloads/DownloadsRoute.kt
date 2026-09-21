package dev.behradhz.meowzix.feature.downloads

import androidx.compose.foundation.layout.Arrangement
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
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.TrackArtwork

@Composable
fun DownloadsRoute(viewModel: DownloadsViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val settings by viewModel.networkSettings.collectAsStateWithLifecycle()
    val selectedChats by viewModel.selectedTelegramChats.collectAsStateWithLifecycle()
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
        if (selectedChats.isNotEmpty()) {
            item { SectionTitle("Telegram sources") }
            items(selectedChats, key = { it.chatId }) { chat ->
                TelegramDownloadSource(chat) { viewModel.downloadAll(chat.chatId) }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionTitle("Downloading now") }
            items(active, key = { it.trackId }) { row -> DownloadRowCard(row, { viewModel.cancel(row.trackId) }, { viewModel.retry(row.trackId) }, { viewModel.remove(row.trackId) }) }
        }
        item { SectionTitle("Download library") }
        if (rest.isEmpty() && active.isEmpty()) {
            item { Text("Use the download icon on a track cover, or download a whole Telegram source above.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)) }
        } else {
            items(rest, key = { it.trackId }) { row -> DownloadRowCard(row, { viewModel.cancel(row.trackId) }, { viewModel.retry(row.trackId) }, { viewModel.remove(row.trackId) }) }
        }
    }
}

@Composable
private fun TelegramDownloadSource(chat: TelegramChatSummary, onDownloadAll: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(chat.profilePhotoRef, chat.title, size = 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Selected Telegram source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f))
            }
            Button(onClick = onDownloadAll, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp)); Text("All")
            }
        }
    }
}

@Composable
private fun DownloadRowCard(row: DownloadRow, onCancel: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(row.artworkRef, row.title, size = 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.status.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                }
                when (row.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    DownloadStatus.FAILED, DownloadStatus.CANCELED -> Button(onClick = onRetry) { Text("Retry") }
                    DownloadStatus.COMPLETED -> OutlinedButton(onClick = onRemove) { Text("Remove") }
                }
            }
            if (row.status == DownloadStatus.DOWNLOADING || row.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.size(8.dp))
                if (row.progress != null) LinearProgressIndicator(progress = { row.progress }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
    DownloadStatus.QUEUED -> "Queued"; DownloadStatus.DOWNLOADING -> "Downloading"; DownloadStatus.COMPLETED -> "Available offline"; DownloadStatus.FAILED -> "Download failed"; DownloadStatus.CANCELED -> "Canceled"
}
