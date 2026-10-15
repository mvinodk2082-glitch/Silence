package com.silence.blocker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.silence.blocker.ui.theme.ScreenTitle
import com.silence.blocker.ui.theme.SilenceCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import com.silence.blocker.BuildConfig
import com.silence.blocker.SilenceApp
import com.silence.blocker.filter.ProtectionProfile
import com.silence.blocker.lists.scheduleListUpdates
import com.silence.blocker.media.MediaAdGuard
import com.silence.blocker.util.UpdateLinks
import com.silence.blocker.vpn.AlwaysOnVpnGuide
import com.silence.blocker.vpn.BatteryHelper
import com.silence.blocker.vpn.PrivateDnsMonitor
import com.silence.blocker.vpn.VpnController
import com.silence.blocker.vpn.VpnHealthMonitor
import kotlinx.coroutines.launch

private data class DnsPreset(val label: String, val ip: String)

private val DNS_PRESETS = listOf(
    DnsPreset("Cloudflare", "1.1.1.1"),
    DnsPreset("Quad9", "9.9.9.9"),
    DnsPreset("Google", "8.8.8.8"),
    DnsPreset("AdGuard DNS", "94.140.14.14"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SilenceApp
    val scope = rememberCoroutineScope()
    val upstream by app.settingsStore.upstreamDns.collectAsStateWithLifecycle("1.1.1.1")
    val profile by app.settingsStore.profile.collectAsStateWithLifecycle(ProtectionProfile.BALANCED)
    val boot by app.settingsStore.startOnBoot.collectAsStateWithLifecycle(false)
    val privateDns by PrivateDnsMonitor.status.collectAsStateWithLifecycle()
    val battery by BatteryHelper.status.collectAsStateWithLifecycle()
    val health by VpnHealthMonitor.snapshot.collectAsStateWithLifecycle()
    val powerSaver by app.settingsStore.powerSaverMode.collectAsStateWithLifecycle(false)
    val alwaysOnConfirmed by app.settingsStore.alwaysOnConfirmed.collectAsStateWithLifecycle(false)
    var upstreamEdit by remember(upstream) { mutableStateOf(upstream) }
    var profileExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BatteryHelper.refresh(context)
        PrivateDnsMonitor.refresh(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings")

        Text("Upstream DNS", fontWeight = FontWeight.SemiBold)
        Text(
            "Allowed domains are resolved here (with fallbacks + TCP if UDP fails). " +
                "Restart protection after changing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DNS_PRESETS.forEach { preset ->
                FilterChip(
                    selected = upstreamEdit == preset.ip,
                    onClick = {
                        upstreamEdit = preset.ip
                        scope.launch { app.settingsStore.setUpstreamDns(preset.ip) }
                    },
                    label = { Text(preset.label) },
                )
            }
        }
        OutlinedTextField(
            value = upstreamEdit,
            onValueChange = { upstreamEdit = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom resolver IP") },
            singleLine = true,
        )
        Button(
            onClick = {
                scope.launch {
                    app.settingsStore.setUpstreamDns(upstreamEdit.trim().ifEmpty { "1.1.1.1" })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save upstream DNS") }

        Text("Private DNS (system)", fontWeight = FontWeight.SemiBold)
        Text(
            privateDns.userMessage
                ?: when (privateDns) {
                    is com.silence.blocker.vpn.PrivateDnsStatus.Off ->
                        "Private DNS is Off — best compatibility with Silence."
                    else -> "Status: $privateDns"
                },
            style = MaterialTheme.typography.bodySmall,
            color = if (privateDns.isWarning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Button(onClick = { PrivateDnsMonitor.refresh(context) }) {
            Text("Recheck Private DNS")
        }

        Text("Protection profile", fontWeight = FontWeight.SemiBold)
        ExposedDropdownMenuBox(
            expanded = profileExpanded,
            onExpandedChange = { profileExpanded = it },
        ) {
            OutlinedTextField(
                value = profile.displayName,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                supportingText = { Text(profile.description) },
            )
            ExposedDropdownMenu(
                expanded = profileExpanded,
                onDismissRequest = { profileExpanded = false },
            ) {
                ProtectionProfile.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.displayName) },
                        onClick = {
                            profileExpanded = false
                            scope.launch {
                                app.settingsStore.setProfile(p)
                                app.listUpdateManager.loadCachedIntoEngine()
                            }
                        },
                    )
                }
            }
        }

        val autoUpdate by app.settingsStore.autoUpdateLists.collectAsStateWithLifecycle(true)
        val wifiOnly by app.settingsStore.updateOnWifiOnly.collectAsStateWithLifecycle(true)

        Text("Reliability", fontWeight = FontWeight.SemiBold)
        Text(
            "Battery: " + if (battery.ignoringOptimizations) {
                "unrestricted ✓"
            } else {
                "optimized (may kill VPN)"
            } + if (battery.powerSaveMode) " · system power save ON" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (battery.needsAttention) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Button(
            onClick = {
                val req = BatteryHelper.requestIgnoreOptimizationsIntent(context)
                try {
                    context.startActivity(req ?: BatteryHelper.openAppBatterySettingsIntent(context))
                } catch (_: Exception) {
                    context.startActivity(BatteryHelper.openAppDetailsIntent(context))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open battery settings") }

        Button(
            onClick = {
                try {
                    context.startActivity(AlwaysOnVpnGuide.openIntentSafe(context))
                } catch (_: Exception) {
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Always-on VPN settings") }
        AlwaysOnVpnGuide.steps.forEach { step ->
            Text("• $step", style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("I’ve enabled Always-on VPN", fontWeight = FontWeight.SemiBold)
                Text(
                    "Hides the Home checklist card",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = alwaysOnConfirmed,
                onCheckedChange = { checked ->
                    scope.launch { app.settingsStore.setAlwaysOnConfirmed(checked) }
                },
            )
        }

        Text(
            "Health: ${health.dnsHandled} DNS · ${health.packetsSeen} packets · ${health.restarts} restarts",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { VpnController.restart(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restart protection now") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Start on boot", fontWeight = FontWeight.SemiBold)
                Text(
                    "Resume after reboot if protection was on (VPN consent still required). Prefer Always-on VPN too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = boot,
                onCheckedChange = { scope.launch { app.settingsStore.setStartOnBoot(it) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Power-saver mode", fontWeight = FontWeight.SemiBold)
                Text(
                    "Longer stats flush interval (60s) to reduce wakeups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = powerSaver,
                onCheckedChange = { checked ->
                    scope.launch { app.settingsStore.setPowerSaverMode(checked) }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-update filter lists", fontWeight = FontWeight.SemiBold)
                Text(
                    "Daily WorkManager refresh of enabled remote packs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoUpdate,
                onCheckedChange = { checked ->
                    scope.launch {
                        app.settingsStore.setAutoUpdateLists(checked)
                        scheduleListUpdates(context, app.settingsStore)
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Update lists on Wi‑Fi only", fontWeight = FontWeight.SemiBold)
                Text(
                    "Avoid large downloads on mobile data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = wifiOnly,
                onCheckedChange = { checked ->
                    scope.launch {
                        app.settingsStore.setUpdateOnWifiOnly(checked)
                        scheduleListUpdates(context, app.settingsStore)
                    }
                },
            )
        }

        Text("Spotify (network design)", fontWeight = FontWeight.SemiBold)
        Text(
            "DNS cannot cleanly remove free Spotify audio ads (music and ads share CDNs). " +
                "Silence excludes Spotify from the VPN so it stays online, then optionally mutes ads at the media layer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val spotifyMute by app.settingsStore.spotifyAdMuteEnabled.collectAsStateWithLifecycle(false)
        val mediaStatus by MediaAdGuard.status.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mute Spotify ads (experimental)", fontWeight = FontWeight.SemiBold)
                Text(
                    "Auto-bypasses Spotify VPN · needs Notification access · not 100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = spotifyMute,
                onCheckedChange = { checked ->
                    MediaAdGuard.setEnabled(context, checked)
                    if (checked && VpnController.state.value == com.silence.blocker.vpn.ProtectionState.ON) {
                        VpnController.markNeedsRestart()
                    }
                },
            )
        }
        Text(
            mediaStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (spotifyMute && !MediaAdGuard.isNotificationAccessEnabled(context)) {
            Button(
                onClick = { MediaAdGuard.openNotificationAccessSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant notification access") }
        }
        if (spotifyMute) {
            Text(
                "Restart protection after enabling so Spotify exclusion applies to the VPN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("About & updates", fontWeight = FontWeight.SemiBold)
        Button(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(UpdateLinks.RELEASES_LATEST)),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check for updates (GitHub)") }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(UpdateLinks.WEBSITE)),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open website") }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(UpdateLinks.PRIVACY)),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Privacy policy") }
        Text(
            "Tip: install Obtainium and add\n${UpdateLinks.OBTAINIUM_HINT}\nfor automatic update alerts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Silence ${BuildConfig.VERSION_NAME}\n" +
                "GPL-3.0 · local DNS filter · no remote VPN · no analytics",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
