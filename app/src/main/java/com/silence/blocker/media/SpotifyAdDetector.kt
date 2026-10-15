package com.silence.blocker.media

import android.media.MediaMetadata
import android.media.session.PlaybackState

/**
 * Listener-side Spotify ad detection from public MediaSession metadata / notification text.
 * Detection only — it never controls Spotify playback.
 *
 * Signals (strongest first), modelled on open-source muters:
 *  - android.media.metadata.ADVERTISEMENT == 1 (Hushify)
 *  - media id / URI starting with "spotify:ad:" (Hushify, MusicPeace, Spotify MPRIS trackid)
 *  - title exactly "Advertisement" / "Spotify Ad" (Mutify, MusicPeace)
 *  - PLAYING with no SKIP_TO_NEXT action plus a short (<=60 s) duration or advertiser-like metadata
 */
object SpotifyAdDetector {
    val SPOTIFY_PACKAGES = setOf("com.spotify.music", "com.spotify.lite")

    const val KEY_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT"
    const val AD_URI_PREFIX = "spotify:ad:"
    const val SHORT_AD_MS = 60_000L

    /** Whole-string (case/punctuation-insensitive) titles that mean an ad. No substring matching. */
    private val exactAdTitles = setOf("advertisement", "spotify ad", "spotify advertisement", "spotify")

    data class Signals(
        val packageName: String?,
        val mediaId: String? = null,
        val uris: List<String> = emptyList(),
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val displayTitle: String? = null,
        val advertisementFlag: Long? = null,
        val isPlaying: Boolean = false,
        /** null when unknown (e.g. notification-only path). */
        val hasSkipNext: Boolean? = null,
        val durationMs: Long = -1L,
        val notificationTitle: String? = null,
        val notificationText: String? = null,
    ) {
        /** Stable-ish identity of the current item, used for counting and watchdog suppression. */
        val itemKey: String get() = mediaId ?: listOf(title, artist, durationMs, notificationTitle).joinToString("|")
    }

    fun isSpotifyPackage(packageName: String?): Boolean =
        packageName != null && packageName in SPOTIFY_PACKAGES

    private fun norm(s: String?): String =
        s.orEmpty().lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun isAd(s: Signals): Boolean {
        if (!isSpotifyPackage(s.packageName)) return false
        if (s.advertisementFlag == 1L) return true
        if (s.mediaId?.startsWith(AD_URI_PREFIX, ignoreCase = true) == true) return true
        if (s.uris.any { it.startsWith(AD_URI_PREFIX, ignoreCase = true) }) return true

        val title = norm(s.title).ifEmpty { norm(s.displayTitle) }
        val nTitle = norm(s.notificationTitle)
        if (title in exactAdTitles - "spotify" || nTitle in exactAdTitles - "spotify") return true
        // "Spotify" as the item title with Spotify/blank artist = house ad
        val artist = norm(s.artist)
        if ((title == "spotify" || nTitle == "spotify") && (artist.isEmpty() || artist == "spotify")) return true

        if (s.isPlaying && s.hasSkipNext == false) {
            val short = s.durationMs in 1..SHORT_AD_MS
            val advertiserLike = artist == "spotify" || (artist.isEmpty() && norm(s.album).isEmpty())
            if (short || advertiserLike) return true
        }
        return false
    }

    fun signalsFrom(
        packageName: String?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState?,
        notificationTitle: String? = null,
        notificationText: String? = null,
    ): Signals {
        val uriKeys = listOf(
            MediaMetadata.METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        )
        val uris = metadata?.let { m -> uriKeys.mapNotNull { runCatching { m.getString(it) }.getOrNull() } }.orEmpty()
        val playing = playbackState?.state == PlaybackState.STATE_PLAYING
        return Signals(
            packageName = packageName,
            mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            uris = uris,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            advertisementFlag = metadata?.takeIf { it.containsKey(KEY_ADVERTISEMENT) }?.getLong(KEY_ADVERTISEMENT),
            isPlaying = playing,
            hasSkipNext = playbackState?.let { (it.actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L },
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L,
            notificationTitle = notificationTitle,
            notificationText = notificationText,
        )
    }

    /** Back-compat wrapper. */
    fun looksLikeAd(
        packageName: String?,
        metadata: MediaMetadata?,
        playbackState: PlaybackState?,
        notificationTitle: String? = null,
        notificationText: String? = null,
    ): Boolean = isAd(signalsFrom(packageName, metadata, playbackState, notificationTitle, notificationText))
}
