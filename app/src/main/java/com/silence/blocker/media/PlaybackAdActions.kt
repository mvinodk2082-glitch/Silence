package com.silence.blocker.media

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silence.blocker.R

/**
 * Local mute of the music stream on this device only. Android has no public API to mute a single
 * other app, so STREAM_MUSIC is muted and *always* restored. Never skips, never touches Spotify.
 * "mutedByUs" is persisted synchronously so a crash/kill can be recovered on next start.
 */
class PlaybackAdActions(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val isMutedByUs: Boolean get() = prefs(appContext).getBoolean(KEY_MUTED, false)

    fun mute() {
        if (isMutedByUs) return
        // Respect a user who already muted: don't take ownership of their mute.
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return
        try {
            prefs(appContext).edit().putBoolean(KEY_MUTED, true).commit()
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            showMutedNotification()
        } catch (e: Exception) {
            Log.w(TAG, "mute failed: ${e.message}")
            prefs(appContext).edit().putBoolean(KEY_MUTED, false).commit()
        }
    }

    fun unmute() = restore(appContext)

    companion object {
        private const val TAG = "PlaybackAdActions"
        private const val PREFS = "silence_media"
        private const val KEY_MUTED = "muted_by_us"
        const val CHANNEL = "silence_ad_mute"
        const val NOTIF_ID = 77
        const val ACTION_UNMUTE_NOW = "com.silence.blocker.media.UNMUTE_NOW"

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Unmute only if we muted; clears the persisted flag. Safe to call any time (crash recovery). */
        fun restore(context: Context) {
            val c = context.applicationContext
            if (!prefs(c).getBoolean(KEY_MUTED, false)) return
            try {
                val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: Exception) {
                Log.w(TAG, "unmute failed: ${e.message}")
            } finally {
                prefs(c).edit().putBoolean(KEY_MUTED, false).commit()
                (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showMutedNotification() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Spotify ad mute", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val pi = PendingIntent.getBroadcast(
            appContext, 0,
            Intent(appContext, UnmuteReceiver::class.java).setAction(ACTION_UNMUTE_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Spotify ad muted")
            .setContentText("Sound returns when music resumes (max 35 s).")
            .addAction(0, "Wrong mute? Unmute now", pi)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: Exception) {}
    }
}

class UnmuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == PlaybackAdActions.ACTION_UNMUTE_NOW) MediaAdGuard.unmuteNow(context)
    }
}
