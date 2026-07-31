package com.silence.blocker.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.os.Build
import android.util.Log

/**
 * Mute music stream during ads; optionally skip.
 * Always pair mute with a guaranteed unmute path.
 */
class PlaybackAdActions(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var mutedByUs = false

    fun onAdDetected(controller: MediaController?, preferSkip: Boolean) {
        if (preferSkip && trySkip(controller)) {
            // Skip may leave a short audible blip; still mute briefly
            mute()
            return
        }
        mute()
    }

    fun onContentDetected() {
        unmute()
    }

    fun forceUnmute() {
        unmute()
    }

    val isMutedByUs: Boolean get() = mutedByUs

    private fun trySkip(controller: MediaController?): Boolean {
        return try {
            val controls = controller?.transportControls ?: return false
            controls.skipToNext()
            true
        } catch (e: Exception) {
            Log.d(TAG, "skip failed: ${e.message}")
            false
        }
    }

    private fun mute() {
        if (mutedByUs) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    0,
                )
            } else {
                @Suppress("DEPRECATION")
                audio.setStreamMute(AudioManager.STREAM_MUSIC, true)
            }
            mutedByUs = true
        } catch (e: Exception) {
            Log.w(TAG, "mute failed: ${e.message}")
        }
    }

    private fun unmute() {
        if (!mutedByUs) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    0,
                )
            } else {
                @Suppress("DEPRECATION")
                audio.setStreamMute(AudioManager.STREAM_MUSIC, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "unmute failed: ${e.message}")
        } finally {
            mutedByUs = false
        }
    }

    companion object {
        private const val TAG = "PlaybackAdActions"
    }
}
