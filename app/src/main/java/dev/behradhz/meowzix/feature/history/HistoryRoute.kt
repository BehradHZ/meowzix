package dev.behradhz.meowzix.feature.history

import android.content.Intent
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.BuildConfig
import dev.behradhz.meowzix.domain.history.ListeningEventType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val privacy by viewModel.privacy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.debugReports.collect { report ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Meowzix personalization debug report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export debug report"))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
        Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Store listening history on this device", modifier = Modifier.weight(1f))
            Switch(privacy.listeningHistoryEnabled, viewModel::setHistoryEnabled)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = viewModel::clear, enabled = rows.isNotEmpty()) { Text("Clear history") }
            OutlinedButton(onClick = viewModel::resetPersonalization, enabled = rows.isNotEmpty()) {
                Text("Reset Smart learning")
            }
        }
        Text(
            "Reset Smart learning keeps your history visible but makes Smart Shuffle learn again only from future listening.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = viewModel::exportPersonalizationDebugReport,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text("Export Smart debug report")
            }
        }
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
