package com.silence.blocker.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Enables [android.media.session.MediaSessionManager.getActiveSessions] on modern Android
 * and supplies notification title fallbacks for ad heuristics.
 */
class SilenceNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        MediaAdGuard.onNotificationAccessChanged(applicationContext, true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaAdGuard.onNotificationAccessChanged(applicationContext, false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!SpotifyAdDetector.isSpotifyPackage(sbn.packageName)) return
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        MediaAdGuard.onSpotifyNotification(applicationContext, title, text)
    }

    companion object {
        private const val TAG = "SilenceNotifListener"
    }
}
