package com.silence.blocker.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silence.blocker.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restarts protection after reboot / package replace if the user left it enabled.
 * Still requires prior VPN consent (Android system policy).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val allowed = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!allowed) return

        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                val settings = SettingsStore(app)
                val enabled = runBlocking { settings.protectionEnabled.first() }
                val boot = runBlocking { settings.startOnBoot.first() }
                // Package replace: resume if protection was enabled (even if boot toggle off)
                val shouldStart = when (action) {
                    Intent.ACTION_MY_PACKAGE_REPLACED -> enabled
                    else -> enabled && boot
                }
                if (shouldStart && VpnController.prepareIntent(app) == null) {
                    // Small delay so ConnectivityService is ready on some OEMs
                    Thread.sleep(1_500)
                    VpnController.start(app)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
