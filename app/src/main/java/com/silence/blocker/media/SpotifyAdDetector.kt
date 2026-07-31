package com.silence.blocker.media

import android.media.MediaMetadata
import android.media.session.PlaybackState

/**
 * Best-effort heuristics for Spotify free-tier audio ads.
 * Spotify changes metadata often — keep conservative and easy to disable.
 */
object SpotifyAdDetector {
    val SPOTIFY_PACKAGES = setOf(
        "com.spotify.music",
        "com.spotify.lite",
    )

    private val adTitlePatterns = listOf(
        Regex("advertisement", RegexOption.IGNORE_CASE),
        Regex("^ad$", RegexOption.IGNORE_CASE),
        Regex("\\bad\\b", RegexOption.IGNORE_CASE),
        Regex("sponsored", RegexOption.IGNORE_CASE),
        Regex("spotify\\s*ad", RegexOption.IGNORE_CASE),
    )

    fun isSpotifyPackage(packageName: String?): Boolean =
        packageName != null && packageName in SPOTIFY_PACKAGES

    fun looksLikeAd(
        packageName: String?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState?,
        notificationTitle: String? = null,
        notificationText: String? = null,
    ): Boolean {
        if (!isSpotifyPackage(packageName)) return false

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L

        val blob = listOf(title, album, artist, displayTitle, notificationTitle, notificationText)
            .filterNotNull()
            .joinToString(" | ")

        if (blob.isNotBlank() && adTitlePatterns.any { it.containsMatchIn(blob) }) {
            return true
        }

        // Very short clips while playing often are ads (music tracks are usually longer)
        val playing = playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing && duration in 1..45_000 && title.isBlank() && artist.isBlank()) {
            return true
        }

        return false
    }
}
