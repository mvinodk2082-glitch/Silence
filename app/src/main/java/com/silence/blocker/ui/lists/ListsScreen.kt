package com.silence.blocker.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silence.blocker.SilenceApp
import com.silence.blocker.filter.FilterCategory
import com.silence.blocker.lists.PackCatalog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ListsScreen() {
    val app = LocalContext.current.applicationContext as SilenceApp
    val scope = rememberCoroutineScope()
    val updateState by app.listUpdateManager.state.collectAsStateWithLifecycle()
    val packStatuses by app.listUpdateManager.packStatuses.collectAsStateWithLifecycle()
    val enabledPacks by app.settingsStore.enabledPackIds.collectAsStateWithLifecycle(
        PackCatalog.defaultEnabledIds(),
    )
    val enabledCats by app.settingsStore.enabledCategories.collectAsStateWithLifecycle(
        FilterCategory.entries.filter { it.enabledByDefault }.map { it.id }.toSet(),
    )
    val autoUpdate by app.settingsStore.autoUpdateLists.collectAsStateWithLifecycle(true)
    val wifiOnly by app.settingsStore.updateOnWifiOnly.collectAsStateWithLifecycle(true)
    val lastUpdate by app.settingsStore.lastListUpdateEpochMs.collectAsStateWithLifecycle(null)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Filter lists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Built-in samples always load. Enable remote packs and update to pull full host lists onto this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${updateState.totalHostsLoaded.coerceAtLeast(app.filterEngine.snapshot.value.listHosts)} hosts active",
                    fontWeight = FontWeight.SemiBold,
                )
                lastUpdate?.let {
                    Text(
                        "Last remote update: ${DateFormat.getDateTimeInstance().format(Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                updateState.lastMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                updateState.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (updateState.isUpdating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                app.listUpdateManager.updateEnabledPacks(respectWifiOnly = wifiOnly)
                            }
                        },
                        enabled = !updateState.isUpdating,
                    ) { Text("Update now") }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                app.listUpdateManager.updateEnabledPacks(
                                    respectWifiOnly = false,
                                )
                            }
                        },
                        enabled = !updateState.isUpdating,
                    ) { Text("Force (any network)") }
                }
            }
        }

        Text("Categories", fontWeight = FontWeight.SemiBold)
        FilterCategory.entries.forEach { category ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(category.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${app.filterEngine.filter.listSize(category)} hosts loaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Switch(
                        checked = category.id in enabledCats,
                        onCheckedChange = { checked ->
                            scope.launch {
                                app.listUpdateManager.setCategoryEnabled(category, checked)
                            }
                        },
                    )
                }
            }
        }

        Text("Remote packs", fontWeight = FontWeight.SemiBold)
        PackCatalog.all.forEach { pack ->
            val status = packStatuses[pack.id]
            val enabled = pack.id in enabledPacks
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Category: ${pack.category.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            val cacheLine = when {
                                status?.busy == true -> "Downloading…"
                                status?.cached == true ->
                                    "Cached: ${status.hostCount} hosts" +
                                        (status.updatedAtEpochMs?.let {
                                            " · ${DateFormat.getDateInstance().format(Date(it))}"
                                        } ?: "")
                                else -> "Not downloaded yet"
                            }
                            Text(
                                cacheLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (status?.busy == true) {
                            CircularProgressIndicator(Modifier.padding(8.dp))
                        } else {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        app.listUpdateManager.setPackEnabled(pack.id, checked)
                                        if (checked && status?.cached != true) {
                                            app.listUpdateManager.updateEnabledPacks(
                                                packId = pack.id,
                                                respectWifiOnly = wifiOnly,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (enabled) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.listUpdateManager.updateEnabledPacks(
                                        packId = pack.id,
                                        respectWifiOnly = false,
                                    )
                                }
                            },
                            enabled = !updateState.isUpdating,
                        ) { Text("Refresh this pack") }
                    }
                }
            }
        }

        Text("Auto-update", fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily background update")
                        Text(
                            "Uses WorkManager every 24h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = { checked ->
                            scope.launch {
                                app.settingsStore.setAutoUpdateLists(checked)
                                com.silence.blocker.lists.scheduleListUpdates(
                                    app,
                                    app.settingsStore,
                                )
                            }
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi‑Fi only")
                        Text(
                            "Skip cellular for large lists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { checked ->
                            scope.launch {
                                app.settingsStore.setUpdateOnWifiOnly(checked)
                                com.silence.blocker.lists.scheduleListUpdates(
                                    app,
                                    app.settingsStore,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
