package com.silence.blocker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silence.blocker.ui.theme.ScreenTitle
import com.silence.blocker.ui.theme.SilenceCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silence.blocker.SilenceApp

@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as SilenceApp
    val stats by app.filterEngine.snapshot.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Statistics")
        SilenceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Blocked today: ${stats.blockedToday}")
                Text("Allowed queries: ${stats.allowedToday}")
                Text("Hosts loaded: ${stats.listHosts}")
            }
        }
        Text("Recent blocks", fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(stats.recentBlocked) { host ->
                SilenceCard(outline = false, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(host, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (stats.recentBlocked.isEmpty()) {
                item {
                    Text(
                        "No blocks yet. Enable protection and browse a site with ads.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
