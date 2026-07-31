package com.silence.blocker.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery optimization / power-save helpers so OEMs do not kill the local VPN.
 */
object BatteryHelper {
    private val _status = MutableStateFlow(BatteryStatus())
    val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    fun refresh(context: Context): BatteryStatus {
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(app.packageName)
        } else {
            true
        }
        val saver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pm.isPowerSaveMode
        } else {
            false
        }
        val resolved = BatteryStatus(
            ignoringOptimizations = ignoring,
            powerSaveMode = saver,
            tip = when {
                !ignoring ->
                    "Battery optimization may pause Silence. Allow unrestricted battery use for reliable protection."
                saver ->
                    "Power save mode is on. Some OEMs still throttle VPNs — disable if ads reappear."
                else -> null
            },
        )
        _status.value = resolved
        return resolved
    }

    /** Opens the system “ignore battery optimizations” dialog for this app. */
    @SuppressLint("BatteryLife")
    fun requestIgnoreOptimizationsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(app.packageName)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
    }

    /** Fallback: open app battery settings page. */
    fun openAppBatterySettingsIntent(context: Context): Intent {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
        }
    }

    fun openAppDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}

data class BatteryStatus(
    val ignoringOptimizations: Boolean = true,
    val powerSaveMode: Boolean = false,
    val tip: String? = null,
) {
    val needsAttention: Boolean get() = !ignoringOptimizations || powerSaveMode
}
