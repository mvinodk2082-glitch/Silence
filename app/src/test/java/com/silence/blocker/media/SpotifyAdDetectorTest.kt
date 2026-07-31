package com.silence.blocker.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotifyAdDetectorTest {
    @Test
    fun detectsAdvertisementInNotificationTitle() {
        val ad = SpotifyAdDetector.looksLikeAd(
            packageName = "com.spotify.music",
            metadata = null,
            playbackState = null,
            notificationTitle = "Advertisement",
            notificationText = "Learn more",
        )
        assertThat(ad).isTrue()
    }

    @Test
    fun ignoresOtherPackages() {
        val ad = SpotifyAdDetector.looksLikeAd(
            packageName = "com.example.music",
            metadata = null,
            playbackState = null,
            notificationTitle = "Advertisement",
            notificationText = null,
        )
        assertThat(ad).isFalse()
    }

    @Test
    fun normalSongTitleNotAd() {
        val ad = SpotifyAdDetector.looksLikeAd(
            packageName = "com.spotify.music",
            metadata = null,
            playbackState = null,
            notificationTitle = "Bohemian Rhapsody",
            notificationText = "Queen",
        )
        assertThat(ad).isFalse()
    }
}
