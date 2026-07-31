package com.silence.blocker.vpn

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects Android Private DNS (DoT) configuration that can bypass local VPN DNS filtering.
 *
 * System settings keys (API 28+):
 * - private_dns_mode: "off" | "opportunistic" | "hostname"
 * - private_dns_specifier: hostname when mode is "hostname"
 */
object PrivateDnsMonitor {
    private val _status = MutableStateFlow<PrivateDnsStatus>(PrivateDnsStatus.Unknown)
    val status: StateFlow<PrivateDnsStatus> = _status.asStateFlow()

    fun refresh(context: Context): PrivateDnsStatus {
        val resolved = read(context)
        _status.value = resolved
        return resolved
    }

    fun read(context: Context): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus.NotApplicable
        }
        return try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
                ?: "off"
            val specifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
            when (mode.lowercase()) {
                "off" -> PrivateDnsStatus.Off
                "opportunistic" -> PrivateDnsStatus.Opportunistic
                "hostname" -> PrivateDnsStatus.Strict(
                    hostname = specifier?.takeIf { it.isNotBlank() } ?: "configured host",
                )
                else -> PrivateDnsStatus.Unknown
            }
        } catch (_: SecurityException) {
            PrivateDnsStatus.Unknown
        } catch (_: Exception) {
            PrivateDnsStatus.Unknown
        }
    }
}

sealed class PrivateDnsStatus {
    data object Unknown : PrivateDnsStatus()
    data object NotApplicable : PrivateDnsStatus()
    data object Off : PrivateDnsStatus()
    /** May still use DoT when available; usually still allows VPN DNS on many devices. */
    data object Opportunistic : PrivateDnsStatus()
    /** Forced Private DNS hostname — likely bypasses Silence. */
    data class Strict(val hostname: String) : PrivateDnsStatus()

    val isWarning: Boolean
        get() = this is Strict || this is Opportunistic

    val userMessage: String?
        get() = when (this) {
            is Strict ->
                "Private DNS is set to “$hostname”. That often bypasses Silence. " +
                    "Set Private DNS to Off (Settings → Network → Private DNS) for full blocking."
            Opportunistic ->
                "Private DNS is Automatic/opportunistic. If ads still load, set Private DNS to Off."
            Off, NotApplicable, Unknown -> null
        }
}
