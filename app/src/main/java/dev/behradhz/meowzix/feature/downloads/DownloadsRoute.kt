package dev.behradhz.meowzix.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.downloads.DownloadStatus

@Composable
fun DownloadsRoute(viewModel: DownloadsViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val settings by viewModel.networkSettings.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Text("Offline", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 20.dp))
        SettingToggle("Offline mode", settings.offlineMode, viewModel::setOfflineMode)
        SettingToggle("Wi-Fi only downloads", settings.wifiOnlyDownloads, viewModel::setWifiOnly)
        SettingToggle("Prefetch next track", settings.prefetchEnabled, viewModel::setPrefetch)
        SettingToggle(
            "Prefetch on metered networks",
            settings.prefetchOnMetered,
            viewModel::setPrefetchOnMetered,
            enabled = settings.prefetchEnabled && !settings.wifiOnlyDownloads,
        )
        if (rows.isEmpty()) {
            Text("Pin a Telegram track from its menu to keep an app-managed offline copy.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows, key = { it.trackId }) { row ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(row.status.label, style = MaterialTheme.typography.bodySmall)
                        row.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                        row.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            when (row.status) {
                                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED ->
                                    OutlinedButton(onClick = { viewModel.cancel(row.trackId) }) { Text("Cancel") }
                                DownloadStatus.FAILED, DownloadStatus.CANCELED ->
                                    Button(onClick = { viewModel.retry(row.trackId) }) { Text("Retry") }
                                DownloadStatus.COMPLETED ->
                                    OutlinedButton(onClick = { viewModel.remove(row.trackId) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private val DownloadStatus.label: String
    get() = when (this) {
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.DOWNLOADING -> "Downloading"
        DownloadStatus.COMPLETED -> "Available offline"
        DownloadStatus.FAILED -> "Download failed"
        DownloadStatus.CANCELED -> "Canceled"
    }
