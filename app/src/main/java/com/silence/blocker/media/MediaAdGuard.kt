package com.silence.blocker.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.silence.blocker.data.SettingsStore
import com.silence.blocker.lists.CompatibilityAllowlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * L3 Spotify ad reduction: media-session mute/skip while Spotify is VPN-excluded.
 */
object MediaAdGuard {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watcherRef = AtomicReference<SpotifyMediaWatcher?>(null)
    private val actionsRef = AtomicReference<PlaybackAdActions?>(null)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _status = MutableStateFlow("Off")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    private var lastNotifTitle: String? = null

    @Volatile
    private var lastNotifText: String? = null

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val cn = ComponentName(context, SilenceNotificationListener::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName &&
                (ComponentName.unflattenFromString(it)?.className?.contains("SilenceNotificationListener") == true ||
                    it.contains(context.packageName))
        } || flat.contains(cn.flattenToString()) || flat.contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Enable experimental mute: ensures Spotify VPN exclude, starts watcher.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val settings = SettingsStore(app)
            settings.setSpotifyAdMuteEnabled(enabled)
            if (enabled) {
                // Auto-exclude Spotify packages so app stays online
                val excluded = settings.excludedPackages.first().toMutableSet()
                CompatibilityAllowlist.suggestedVpnExcludes.keys
                    .filter { it.contains("spotify") }
                    .forEach { excluded.add(it) }
                settings.setExcludedPackages(excluded)
                launch(Dispatchers.Main) {
                    startWatcher(app)
                    _status.value = if (isNotificationAccessEnabled(app)) {
                        "On — watching Spotify (grant notification access if mute never triggers)"
                    } else {
                        "On — enable Notification access for Silence (required)"
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    stopWatcher()
                    _status.value = "Off"
                }
            }
        }
    }

    fun startIfEnabled(context: Context) {
        scope.launch(Dispatchers.IO) {
            val on = SettingsStore(context.applicationContext).spotifyAdMuteEnabled.first()
            if (on) {
                launch(Dispatchers.Main) { startWatcher(context.applicationContext) }
            }
        }
    }

    fun onNotificationAccessChanged(context: Context, enabled: Boolean) {
        if (enabled && _active.value) {
            // Restart watcher to attach session listener with permission
            stopWatcher()
            startWatcher(context.applicationContext)
            _status.value = "On — notification access granted"
        }
    }

    fun onSpotifyNotification(context: Context, title: String?, text: String?) {
        lastNotifTitle = title
        lastNotifText = text
        // Heuristic-only path when sessions empty
        if (!_active.value) return
        val ad = SpotifyAdDetector.looksLikeAd(
            packageName = "com.spotify.music",
            metadata = null,
            playbackState = null,
            notificationTitle = title,
            notificationText = text,
        )
        val actions = actionsRef.get() ?: return
        if (ad) {
            actions.onAdDetected(null, preferSkip = false)
            _status.value = "Muted ad (notification heuristic)"
        }
    }

    private fun startWatcher(context: Context) {
        if (watcherRef.get() != null) {
            _active.value = true
            return
        }
        val actions = PlaybackAdActions(context)
        actionsRef.set(actions)
        val watcher = SpotifyMediaWatcher(context, actions, preferSkip = true)
        watcherRef.set(watcher)
        watcher.start()
        _active.value = true
        Log.i(TAG, "MediaAdGuard started")
    }

    private fun stopWatcher() {
        watcherRef.getAndSet(null)?.stop()
        actionsRef.getAndSet(null)?.forceUnmute()
        _active.value = false
        Log.i(TAG, "MediaAdGuard stopped")
    }

    private const val TAG = "MediaAdGuard"
}
