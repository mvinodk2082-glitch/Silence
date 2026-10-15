package com.silence.blocker.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.silence.blocker.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val settings = SettingsStore(app)
                val enabled = settings.protectionEnabled.first()
                val boot = settings.startOnBoot.first()
                // Package replace: resume if protection was enabled (even if boot toggle off)
                val shouldStart = when (action) {
                    Intent.ACTION_MY_PACKAGE_REPLACED -> enabled
                    else -> enabled && boot
                }
                // If Always-on VPN owns startup, the system starts us; don't double-start.
                val alwaysOn = action != Intent.ACTION_MY_PACKAGE_REPLACED && isAlwaysOnUs(app)
                if (shouldStart && !alwaysOn && VpnController.prepareIntent(app) == null) {
                    VpnController.start(app)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun isAlwaysOnUs(context: Context): Boolean = try {
        android.provider.Settings.Secure.getString(context.contentResolver, "always_on_vpn_app") == context.packageName
    } catch (_: Exception) {
        false
    }
}
