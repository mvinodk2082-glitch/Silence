package com.silence.blocker.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silence.blocker.SilenceApp
import com.silence.blocker.apps.InstalledAppsRepository
import com.silence.blocker.vpn.ProtectionState
import com.silence.blocker.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-app VPN exclusions via launchable app picker + manual package entry.
 */
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SilenceApp
    val scope = rememberCoroutineScope()
    val excluded by app.settingsStore.excludedPackages.collectAsStateWithLifecycle(emptySet())
    val vpnState by VpnController.state.collectAsStateWithLifecycle()
    val needsRestart by VpnController.needsRestart.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<InstalledAppsRepository.AppEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            InstalledAppsRepository(context.packageManager).launchableApps()
                .filter { it.packageName != context.packageName }
        }
        loading = false
    }

    val filtered = remember(apps, query, excluded) {
        val q = query.trim().lowercase()
        apps.filter { entry ->
            if (q.isEmpty()) true
            else entry.label.lowercase().contains(q) || entry.packageName.lowercase().contains(q)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("App exclusions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Excluded apps bypass Silence’s local VPN (useful for banking apps). " +
                "${excluded.size} excluded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (needsRestart) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restart protection to apply exclusions", fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { VpnController.restart(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restart now") }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search apps or package name") },
            singleLine = true,
        )

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading installed apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Excluded-only section when searching empty and some excluded
            if (query.isEmpty() && excluded.isNotEmpty()) {
                item {
                    Text("Excluded", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                }
                items(apps.filter { it.packageName in excluded }, key = { "ex-${it.packageName}" }) { entry ->
                    AppRow(
                        label = entry.label,
                        packageName = entry.packageName,
                        excluded = true,
                        onToggle = { on ->
                            scope.launch {
                                val next = if (on) excluded + entry.packageName else excluded - entry.packageName
                                app.settingsStore.setExcludedPackages(next)
                                if (vpnState == ProtectionState.ON) {
                                    VpnController.markNeedsRestart()
                                }
                            }
                        },
                    )
                }
                item {
                    Text("All apps", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            items(filtered, key = { it.packageName }) { entry ->
                AppRow(
                    label = entry.label,
                    packageName = entry.packageName,
                    excluded = entry.packageName in excluded,
                    onToggle = { on ->
                        scope.launch {
                            val next = if (on) excluded + entry.packageName else excluded - entry.packageName
                            app.settingsStore.setExcludedPackages(next)
                            if (vpnState == ProtectionState.ON) {
                                VpnController.markNeedsRestart()
                            }
                        }
                    },
                )
            }

            if (!loading && filtered.isEmpty()) {
                item {
                    Text(
                        "No matching apps. You can still type a full package name if needed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    label: String,
    packageName: String,
    excluded: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = excluded, onCheckedChange = onToggle)
    }
}
