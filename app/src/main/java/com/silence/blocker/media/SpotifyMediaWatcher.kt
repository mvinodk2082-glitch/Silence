package com.silence.blocker.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches active media sessions for Spotify and applies ad actions.
 * Requires the app's NotificationListener to be enabled so
 * [MediaSessionManager.getActiveSessions] returns data on modern Android.
 */
class SpotifyMediaWatcher(
    private val context: Context,
    private val actions: PlaybackAdActions,
    private val onNewAd: () -> Unit = {},
    private val onMuteChanged: (Boolean) -> Unit = {},
) {
    private val decider = MuteDecider()
    @Volatile private var notifTitle: String? = null
    @Volatile private var notifText: String? = null
    private val watchdog = object : Runnable {
        override fun run() {
            apply(decider.onTick(android.os.SystemClock.elapsedRealtime()))
            if (decider.muted) handler.postDelayed(this, 1_000)
        }
    }

    /** Notification text is only an extra signal into the same evaluator (with unmute path). */
    fun onNotification(title: String?, text: String?) {
        notifTitle = title; notifText = text
        handler.post { evaluateAll() }
    }

    fun userUnmute() { handler.post { apply(decider.userUnmute()) } }

    private fun apply(a: MuteDecider.Action) {
        when (a) {
            MuteDecider.Action.MUTE -> {
                actions.mute(); onMuteChanged(actions.isMutedByUs)
                handler.removeCallbacks(watchdog); handler.postDelayed(watchdog, 1_000)
            }
            MuteDecider.Action.UNMUTE -> { actions.unmute(); handler.removeCallbacks(watchdog); onMuteChanged(false) }
            MuteDecider.Action.NONE -> {}
        }
    }
    private val running = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = mutableMapOf<String, MediaController>()
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            bindControllers(list)
            evaluateAll()
        }
        sessionListener = listener
        try {
            msm.addOnActiveSessionsChangedListener(listener, notificationListenerComponent())
        } catch (e: SecurityException) {
            Log.w(TAG, "Need notification listener access: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "session listener failed: ${e.message}")
        }
        PlaybackAdActions.restore(context)
        refreshControllers()
        evaluateAll()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionListener?.let {
            try {
                msm.removeOnActiveSessionsChangedListener(it)
            } catch (_: Exception) {
            }
        }
        sessionListener = null
        synchronized(controllers) {
            controllers.values.forEach { c ->
                try {
                    c.unregisterCallback(callback)
                } catch (_: Exception) {
                }
            }
            controllers.clear()
        }
        handler.removeCallbacks(watchdog)
        actions.unmute()
    }

    private fun notificationListenerComponent(): ComponentName =
        ComponentName(context, SilenceNotificationListener::class.java)

    private fun refreshControllers() {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = try {
            msm.getActiveSessions(notificationListenerComponent())
        } catch (e: SecurityException) {
            Log.d(TAG, "getActiveSessions denied — enable notification access")
            emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "getActiveSessions: ${e.message}")
            emptyList()
        }
        bindControllers(list)
    }

    private fun bindControllers(list: List<MediaController>?) {
        val spotify = list.orEmpty().filter {
            SpotifyAdDetector.isSpotifyPackage(it.packageName)
        }
        synchronized(controllers) {
            val keys = spotify.map { it.sessionToken.toString() }.toSet()
            val stale = controllers.keys.filter { it !in keys }
            stale.forEach { k ->
                try {
                    controllers.remove(k)?.unregisterCallback(callback)
                } catch (_: Exception) {
                }
            }
            for (c in spotify) {
                val key = c.sessionToken.toString()
                if (key !in controllers) {
                    try {
                        c.registerCallback(callback, handler)
                        controllers[key] = c
                    } catch (e: Exception) {
                        Log.d(TAG, "registerCallback: ${e.message}")
                    }
                }
            }
        }
    }

    private fun evaluateAll() {
        if (!running.get()) return
        val snapshot: List<MediaController>
        synchronized(controllers) { snapshot = controllers.values.toList() }
        var adKey: String? = null
        if (snapshot.isEmpty()) {
            // Session gone: only the notification can signal; unknown playing state -> treat as playing ad only on strong text
            val sig = SpotifyAdDetector.Signals("com.spotify.music", notificationTitle = notifTitle, notificationText = notifText)
            if (notifTitle != null && SpotifyAdDetector.isAd(sig)) adKey = "notif|" + sig.itemKey
        } else {
            for (c in snapshot) {
                val sig = SpotifyAdDetector.signalsFrom(c.packageName, c.metadata, c.playbackState, notifTitle, notifText)
                if (sig.isPlaying && SpotifyAdDetector.isAd(sig)) { adKey = sig.itemKey; break }
            }
        }
        val before = decider.newAdCount
        val action = decider.onEvaluate(adKey, android.os.SystemClock.elapsedRealtime())
        apply(action)
        if (decider.newAdCount != before) onNewAd()
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            evaluateAll()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            evaluateAll()
        }

        override fun onSessionDestroyed() {
            refreshControllers()
            evaluateAll()
        }
    }

    companion object {
        private const val TAG = "SpotifyMediaWatcher"
    }
}
