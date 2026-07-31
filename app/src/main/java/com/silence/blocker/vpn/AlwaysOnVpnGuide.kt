package com.silence.blocker.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Always-on VPN is a **system** setting (Android 7+). Apps cannot enable it silently.
 * We deep-link to VPN settings and explain the steps.
 */
object AlwaysOnVpnGuide {
    val steps: List<String> = listOf(
        "Open system VPN settings (button below).",
        "Tap the gear / settings icon next to Silence.",
        "Turn on “Always-on VPN”.",
        "Optional: enable “Block connections without VPN” only if you want hard fail-closed (can break captive portals).",
        "Return here and confirm protection stays on after reboot.",
    )

    fun openVpnSettingsIntent(): Intent {
        // Prefer VPN-specific panel when available
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_VPN_SETTINGS)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
    }

    fun openIntentSafe(context: Context): Intent {
        val primary = openVpnSettingsIntent()
        return if (primary.resolveActivity(context.packageManager) != null) {
            primary
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Android does not expose a public API for “is always-on VPN enabled for my app”
     * on all versions without privileged access. We track a user-confirmed checklist instead.
     */
    const val USER_CONFIRMED_PREF = "always_on_confirmed"
}
