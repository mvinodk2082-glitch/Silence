package com.silence.blocker.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.silence.blocker.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProtectionState {
    OFF,
    REQUESTING_PERMISSION,
    STARTING,
    ON,
    RESTARTING,
    ERROR,
}

object VpnController {
    private val _state = MutableStateFlow(ProtectionState.OFF)
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when exclusions change while VPN is up — UI prompts restart. */
    private val _needsRestart = MutableStateFlow(false)
    val needsRestart: StateFlow<Boolean> = _needsRestart.asStateFlow()

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun setState(state: ProtectionState, error: String? = null) {
        _state.value = state
        _error.value = error
        if (state == ProtectionState.ON) {
            _needsRestart.value = false
        }
    }

    fun markNeedsRestart() {
        if (_state.value == ProtectionState.ON || _state.value == ProtectionState.STARTING) {
            _needsRestart.value = true
        }
    }

    fun clearNeedsRestart() {
        _needsRestart.value = false
    }

    fun start(context: Context) {
        val app = context.applicationContext
        val prepare = VpnService.prepare(app)
        if (prepare != null) {
            _state.value = ProtectionState.REQUESTING_PERMISSION
            return
        }
        _state.value = ProtectionState.STARTING
        _error.value = null
        val intent = Intent(app, SilenceVpnService::class.java).apply {
            action = SilenceVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(app, intent)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, SilenceVpnService::class.java).apply {
            action = SilenceVpnService.ACTION_STOP
        }
        try {
            app.startService(intent)
        } catch (_: Exception) {
            ContextCompat.startForegroundService(app, intent)
        }
        _state.value = ProtectionState.OFF
        _needsRestart.value = false
    }

    /**
     * Stop then start so new exclusions / DNS settings apply.
     * Service handles sequenced restart via ACTION_RESTART.
     */
    fun restart(context: Context) {
        val app = context.applicationContext
        if (VpnService.prepare(app) != null) {
            _state.value = ProtectionState.REQUESTING_PERMISSION
            return
        }
        _state.value = ProtectionState.RESTARTING
        _error.value = null
        VpnHealthMonitor.markRestart()
        val intent = Intent(app, SilenceVpnService::class.java).apply {
            action = SilenceVpnService.ACTION_RESTART
        }
        ContextCompat.startForegroundService(app, intent)
    }

    suspend fun setEnabledPreference(context: Context, enabled: Boolean) {
        SettingsStore(context.applicationContext).setProtectionEnabled(enabled)
    }
}
