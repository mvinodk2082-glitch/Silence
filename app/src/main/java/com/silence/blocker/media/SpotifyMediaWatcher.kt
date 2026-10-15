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
    private val preferSkip: Boolean = true,
) {
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
        actions.forceUnmute()
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
        val snapshot: List<MediaController>
        synchronized(controllers) {
            snapshot = controllers.values.toList()
        }
        if (snapshot.isEmpty()) {
            actions.onContentDetected()
            return
        }
        var anyAd = false
        var adController: MediaController? = null
        for (c in snapshot) {
            if (SpotifyAdDetector.looksLikeAd(c.packageName, c.metadata, c.playbackState)) {
                anyAd = true
                adController = c
                break
            }
        }
        if (anyAd) {
            actions.onAdDetected(adController, preferSkip)
        } else {
            actions.onContentDetected()
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            evaluateAll()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            evaluateAll()
        }
    }

    companion object {
        private const val TAG = "SpotifyMediaWatcher"
    }
}
