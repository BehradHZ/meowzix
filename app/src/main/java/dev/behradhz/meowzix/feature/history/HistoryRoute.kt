package dev.behradhz.meowzix.feature.history

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.history.ListeningEventType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val privacy by viewModel.privacy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Store listening history on this device", modifier = Modifier.weight(1f))
            Switch(privacy.listeningHistoryEnabled, viewModel::setHistoryEnabled)
        }
        Button(onClick = viewModel::clear, enabled = rows.isNotEmpty()) { Text("Clear history") }
        Text("Recently played", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        val recent = rows.filter { it.type == ListeningEventType.PLAY_STARTED }.distinctBy { it.trackId }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recent, key = { it.trackId }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.title, modifier = Modifier.weight(1f))
                    Text(
                        DateTimeFormatter.ofPattern("MMM d, HH:mm").format(row.occurredAt.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
