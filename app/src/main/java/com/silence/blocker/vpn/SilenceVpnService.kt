package com.silence.blocker.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.silence.blocker.MainActivity
import com.silence.blocker.R
import com.silence.blocker.SilenceApp
import com.silence.blocker.data.SettingsStore
import com.silence.blocker.data.StatsStore
import com.silence.blocker.dns.DnsServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket

/**
 * Local-only VPN used as a DNS choke point. No remote VPN server.
 *
 * Phase 3: restart action, health watchdog, battery-aware flush intervals,
 * throttled notification updates, sticky recovery.
 */
class SilenceVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var loopJob: Job? = null
    private var statsJob: Job? = null
    private var healthJob: Job? = null
    private var notifyJob: Job? = null
    private var packetLoop: PacketLoop? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var starting = false
    @Volatile private var activeDns: DnsServer? = null
    private var restartAttempts = 0
    private var screenReceiver: android.content.BroadcastReceiver? = null

    private fun isScreenOn(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    private fun flushStats() {
        val app = application as com.silence.blocker.SilenceApp
        val snap = app.filterEngine.snapshotNow()
        serviceScope.launch { StatsStore(applicationContext).saveToday(snap.blockedToday, snap.allowedToday) }
        VpnHealthMonitor.publish()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> flushStats()
                    Intent.ACTION_SCREEN_ON -> {
                        PrivateDnsMonitor.refresh(applicationContext)
                        BatteryHelper.refresh(applicationContext)
                    }
                }
            }
        }
        registerReceiver(r, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        screenReceiver = r
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenReceiver = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                tearDown(clearEnabledPreference = true, stopService = true)
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                serviceScope.launch {
                    tearDown(clearEnabledPreference = false, stopService = false)
                    delay(350)
                    startProtection()
                }
            }
            ACTION_START, null -> startProtection()
        }
        // Sticky: system may recreate after low-memory kill if user wanted protection
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        // If system recreated us after kill, try resume when preference says so
        serviceScope.launch {
            val enabled = SettingsStore(applicationContext).protectionEnabled.first()
            if (enabled && vpnInterface == null && VpnController.prepareIntent(this@SilenceVpnService) == null) {
                // Don't auto-start without explicit START if we were just created empty —
                // sticky redelivery will call onStartCommand with null action.
            }
        }
    }

    private fun startProtection() {
        if (starting && vpnInterface != null) return
        startForeground(NOTIFICATION_ID, buildNotification(0, healthy = true))
        if (vpnInterface != null) {
            VpnController.setState(ProtectionState.ON)
            PrivateDnsMonitor.refresh(this)
            BatteryHelper.refresh(this)
            return
        }
        if (starting) return
        starting = true

        serviceScope.launch {
            try {
                val settings = SettingsStore(applicationContext)
                val statsStore = StatsStore(applicationContext)
                val upstream = settings.upstreamDns.first()
                val excludes = settings.excludedPackages.first()
                val powerSaverFlush = settings.powerSaverMode.first()

                val app = application as SilenceApp
                val saved = statsStore.loadToday()
                app.filterEngine.hydrateCounters(saved.blocked, saved.allowed, saved.day)
                app.listUpdateManager.loadCachedIntoEngine()
                app.filterEngine.applyUserRules(
                    allow = settings.allowlist.first(),
                    block = settings.blocklist.first(),
                )

                PrivateDnsMonitor.refresh(applicationContext)
                BatteryHelper.refresh(applicationContext)

                val dnsServer = DnsServer(app.filterEngine).also {
                    it.setUpstream(upstream)
                }
                activeDns = dnsServer

                val builder = Builder()
                    .setSession(getString(R.string.vpn_session_name))
                    .setMtu(1500)
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(FAKE_DNS)
                    .addRoute(FAKE_DNS, 32)
                    .setBlocking(true)

                try {
                    builder.addAddress(VPN_ADDRESS_V6, 128)
                    builder.addDnsServer(FAKE_DNS_V6)
                    builder.addRoute(FAKE_DNS_V6, 128)
                } catch (_: Exception) {
                }

                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {
                }
                for (pkg in excludes) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: Exception) {
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                val iface = builder.establish()
                if (iface == null) {
                    VpnController.setState(ProtectionState.ERROR, "VPN interface failed to establish")
                    starting = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                vpnInterface = iface

                val input = FileInputStream(iface.fileDescriptor)
                val output = FileOutputStream(iface.fileDescriptor)
                val loop = PacketLoop(
                    dnsServer = dnsServer,
                    protectUDP = { socket: DatagramSocket -> protect(socket) },
                    protectTCP = { socket: Socket -> protect(socket) },
                    vpnInput = input,
                    vpnOutput = output,
                )
                packetLoop = loop

                VpnController.setState(ProtectionState.ON)
                VpnController.clearNeedsRestart()
                settings.setProtectionEnabled(true)
                registerNetworkMonitor()

                registerScreenReceiver()

                // SL-04: notification refresh >=60 s, only when screen on and count changed
                notifyJob = launch {
                    var lastBlocked = -1L
                    while (isActive) {
                        if (isScreenOn()) {
                            val snap = app.filterEngine.snapshotNow()
                            if (snap.blockedToday != lastBlocked) {
                                lastBlocked = snap.blockedToday
                                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                                nm.notify(NOTIFICATION_ID, buildNotification(snap.blockedToday, healthy = true))
                            }
                        }
                        delay(NOTIFY_INTERVAL_MS)
                    }
                }

                // SL-04: stats persisted on screen-off / stop, plus slow fallback
                statsJob = launch {
                    while (isActive) {
                        delay(STATS_FLUSH_MS)
                        flushStats()
                    }
                }

                // SL-04: no health poll; restart when the loop exits unexpectedly, with backoff
                loopJob = launch {
                    loop.start()
                    if (!isActive || packetLoop !== loop) return@launch
                    if (!settings.protectionEnabled.first()) return@launch
                    VpnController.setState(ProtectionState.ERROR, "DNS loop stopped — recovering…")
                    val backoff = (1000L shl restartAttempts.coerceAtMost(5)).coerceAtMost(30_000L)
                    restartAttempts++
                    serviceScope.launch {
                        tearDown(clearEnabledPreference = false, stopService = false)
                        delay(backoff)
                        startProtection()
                    }
                }
                launch { delay(120_000); restartAttempts = 0 }
            } catch (e: Exception) {
                VpnController.setState(ProtectionState.ERROR, e.message ?: "Unknown error")
                tearDown(clearEnabledPreference = false, stopService = true)
            } finally {
                starting = false
            }
        }
    }

    private fun registerNetworkMonitor() {
        unregisterNetworkMonitor()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeDns?.flushCache()
                PrivateDnsMonitor.refresh(applicationContext)
            }

            override fun onLost(network: Network) {
                activeDns?.flushCache()
                PrivateDnsMonitor.refresh(applicationContext)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                PrivateDnsMonitor.refresh(applicationContext)
            }
        }
        networkCallback = callback
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )
            }
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun unregisterNetworkMonitor() {
        val callback = networkCallback ?: return
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    /**
     * @param clearEnabledPreference user turned protection off
     * @param stopService end the Android service
     */
    private fun tearDown(clearEnabledPreference: Boolean, stopService: Boolean) {
        if (packetLoop != null) flushStats()
        unregisterScreenReceiver()
        activeDns = null
        packetLoop?.stop()
        packetLoop = null
        loopJob?.cancel()
        loopJob = null
        statsJob?.cancel()
        statsJob = null
        healthJob?.cancel()
        healthJob = null
        notifyJob?.cancel()
        notifyJob = null
        unregisterNetworkMonitor()

        try {
            val app = application as? SilenceApp
            if (app != null) {
                val snap = app.filterEngine.snapshotNow()
                runBlocking {
                    StatsStore(applicationContext).saveToday(snap.blockedToday, snap.allowedToday)
                    if (clearEnabledPreference) {
                        SettingsStore(applicationContext).setProtectionEnabled(false)
                    }
                }
            } else if (clearEnabledPreference) {
                runBlocking {
                    SettingsStore(applicationContext).setProtectionEnabled(false)
                }
            }
        } catch (_: Exception) {
            if (clearEnabledPreference) {
                try {
                    runBlocking {
                        SettingsStore(applicationContext).setProtectionEnabled(false)
                    }
                } catch (_: Exception) {
                }
            }
        }

        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        starting = false

        if (clearEnabledPreference) {
            VpnController.setState(ProtectionState.OFF)
        }
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        packetLoop?.stop()
        unregisterNetworkMonitor()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        // User or system disconnected VPN
        tearDown(clearEnabledPreference = true, stopService = true)
        super.onRevoke()
    }

    private fun buildNotification(blockedToday: Long, healthy: Boolean): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val privateDns = PrivateDnsMonitor.status.value
        val battery = BatteryHelper.status.value
        val title = when {
            privateDns is PrivateDnsStatus.Strict -> "Silence on · Private DNS warning"
            battery.needsAttention && !battery.ignoringOptimizations -> "Silence on · Check battery"
            !healthy -> "Silence · recovering"
            else -> getString(R.string.notification_title)
        }
        val health = VpnHealthMonitor.snapshot.value
        val text = buildString {
            append(getString(R.string.notification_text, blockedToday))
            if (health.dnsHandled > 0) {
                append(" · ")
                append(health.dnsHandled)
                append(" DNS")
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.silence.blocker.vpn.START"
        const val ACTION_STOP = "com.silence.blocker.vpn.STOP"
        const val ACTION_RESTART = "com.silence.blocker.vpn.RESTART"
        const val CHANNEL_ID = "silence_protection"
        const val NOTIFICATION_ID = 42
        const val VPN_ADDRESS = "10.10.10.2"
        const val FAKE_DNS = "10.10.10.1"
        const val VPN_ADDRESS_V6 = "fd00:10:10:10::2"
        const val FAKE_DNS_V6 = "fd00:10:10:10::1"
        private const val STATS_FLUSH_MS = 300_000L
        private const val NOTIFY_INTERVAL_MS = 60_000L
    }
}
