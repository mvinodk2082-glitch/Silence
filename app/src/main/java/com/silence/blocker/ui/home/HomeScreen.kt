package com.silence.blocker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.silence.blocker.ui.theme.ScreenTitle
import com.silence.blocker.ui.theme.SilenceCard
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import com.silence.blocker.SilenceApp
import com.silence.blocker.media.MediaAdGuard
import com.silence.blocker.vpn.AlwaysOnVpnGuide
import com.silence.blocker.vpn.BatteryHelper
import com.silence.blocker.vpn.PrivateDnsMonitor
import com.silence.blocker.vpn.PrivateDnsStatus
import com.silence.blocker.vpn.ProtectionState
import com.silence.blocker.vpn.VpnController
import com.silence.blocker.vpn.VpnHealthMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SilenceApp
    val scope = rememberCoroutineScope()
    val state by VpnController.state.collectAsStateWithLifecycle()
    val error by VpnController.error.collectAsStateWithLifecycle()
    val needsRestart by VpnController.needsRestart.collectAsStateWithLifecycle()
    val stats by app.filterEngine.snapshot.collectAsStateWithLifecycle()
    val privateDns by PrivateDnsMonitor.status.collectAsStateWithLifecycle()
    val battery by BatteryHelper.status.collectAsStateWithLifecycle()
    val health by VpnHealthMonitor.snapshot.collectAsStateWithLifecycle()
    val alwaysOnConfirmed by app.settingsStore.alwaysOnConfirmed.collectAsStateWithLifecycle(false)
    val spotifyMute by app.settingsStore.spotifyAdMuteEnabled.collectAsStateWithLifecycle(false)
    val adsMuted by app.settingsStore.adsMuted.collectAsStateWithLifecycle(com.silence.blocker.data.SettingsStore.AdsMuted(0, 0))
    val mutedNow by MediaAdGuard.mutedNow.collectAsStateWithLifecycle()
    val batteryDismissed by app.settingsStore.batteryTipDismissed.collectAsStateWithLifecycle(false)

    LaunchedEffect(Unit) {
        PrivateDnsMonitor.refresh(context)
        BatteryHelper.refresh(context)
        while (true) {
            delay(10_000)
            PrivateDnsMonitor.refresh(context)
            BatteryHelper.refresh(context)
            VpnHealthMonitor.publish()
        }
    }

    val isOn = state == ProtectionState.ON
    val statusLabel = when (state) {
        ProtectionState.OFF -> "Protection off"
        ProtectionState.REQUESTING_PERMISSION -> "Waiting for VPN permission…"
        ProtectionState.STARTING -> "Starting local filter…"
        ProtectionState.RESTARTING -> "Restarting…"
        ProtectionState.ON -> "Protection active"
        ProtectionState.ERROR -> "Error"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Silence",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
        // Calm status hero
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "${stats.blockedToday}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "blocked today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${stats.listHosts} hosts · ${stats.allowedToday} allowed" +
                    if (health.dnsHandled > 0) " · ${health.dnsHandled} DNS handled" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        when {
            isOn || state == ProtectionState.RESTARTING -> {
                OutlinedButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state != ProtectionState.RESTARTING,
                ) { Text("Turn protection off") }
                if (isOn) {
                    TextButton(onClick = { VpnController.restart(context) }) {
                        Text("Restart protection")
                    }
                }
            }
            else -> {
                Button(
                    onClick = onEnable,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state != ProtectionState.STARTING &&
                        state != ProtectionState.REQUESTING_PERMISSION,
                ) { Text("Turn protection on") }
            }
        }


        if (needsRestart && isOn) {
            SilenceCard(containerColor = MaterialTheme.colorScheme.primaryContainer, outline = false) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restart required", fontWeight = FontWeight.SemiBold)
                    Text(
                        "App exclusions or settings changed. Restart protection to apply them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { VpnController.restart(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restart protection") }
                }
            }
        }

        privateDns.userMessage?.let { message ->
            WarningCard(
                title = if (privateDns is PrivateDnsStatus.Strict) {
                    "Private DNS may bypass Silence"
                } else {
                    "Private DNS tip"
                },
                body = message,
                actionLabel = "Recheck",
                onAction = { PrivateDnsMonitor.refresh(context) },
            )
        }

        if (!batteryDismissed && battery.needsAttention) {
            WarningCard(
                title = if (!battery.ignoringOptimizations) {
                    "Allow unrestricted battery"
                } else {
                    "Power save is on"
                },
                body = battery.tip ?: "",
                actionLabel = if (!battery.ignoringOptimizations) "Fix battery" else "Recheck",
                onAction = {
                    if (!battery.ignoringOptimizations) {
                        val req = BatteryHelper.requestIgnoreOptimizationsIntent(context)
                        try {
                            context.startActivity(req ?: BatteryHelper.openAppBatterySettingsIntent(context))
                        } catch (_: Exception) {
                            context.startActivity(BatteryHelper.openAppDetailsIntent(context))
                        }
                    } else {
                        BatteryHelper.refresh(context)
                    }
                },
                secondaryLabel = "Dismiss",
                onSecondary = {
                    scope.launch { app.settingsStore.setBatteryTipDismissed(true) }
                },
            )
        }

        if (!alwaysOnConfirmed) {
            SilenceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Always-on VPN (recommended)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Keeps Silence as the system VPN after reboot and when other apps try to take over. " +
                            "This is a system setting — Silence cannot enable it silently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AlwaysOnVpnGuide.steps.take(3).forEach { step ->
                        Text("• $step", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            try {
                                context.startActivity(AlwaysOnVpnGuide.openIntentSafe(context))
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open VPN settings") }
                    TextButton(
                        onClick = {
                            scope.launch { app.settingsStore.setAlwaysOnConfirmed(true) }
                        },
                    ) { Text("I’ve enabled Always-on / hide this") }
                }
            }
        }


        if (spotifyMute || adsMuted.total > 0) {
            SilenceCard {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Spotify ads muted", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${adsMuted.today} today · ${adsMuted.total} total · local mute only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mutedNow) {
                        TextButton(onClick = { MediaAdGuard.unmuteNow(context) }) { Text("Wrong mute? Unmute now") }
                    }
                }
            }
        }

        SilenceCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Silence opens a local VPN tunnel and becomes your DNS (IPv4 + IPv6). " +
                        "Ad, tracker, and media-ad domains get sinkholed. " +
                        "Other traffic never leaves your device through us.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (stats.recentBlocked.isNotEmpty()) {
            SilenceCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Recently blocked", style = MaterialTheme.typography.titleSmall)
                    stats.recentBlocked.take(8).forEach { host ->
                        Text(
                            host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    SilenceCard(containerColor = MaterialTheme.colorScheme.errorContainer, outline = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}
