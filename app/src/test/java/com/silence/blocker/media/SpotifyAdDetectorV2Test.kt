package com.silence.blocker.media

import com.google.common.truth.Truth.assertThat
import com.silence.blocker.media.SpotifyAdDetector.Signals
import org.junit.Test

class SpotifyAdDetectorV2Test {
    private fun sp(
        title: String? = null, artist: String? = null, album: String? = null, mediaId: String? = null,
        uris: List<String> = emptyList(), playing: Boolean = true, skip: Boolean? = true, dur: Long = 210_000,
        flag: Long? = null, nTitle: String? = null,
    ) = Signals("com.spotify.music", mediaId, uris, title, artist, album, null, flag, playing, skip, dur, nTitle, null)

    // True positives
    @Test fun adMediaId() = assertThat(SpotifyAdDetector.isAd(sp(title = "Anything", artist = "Brand", mediaId = "spotify:ad:000000012c603a6c"))).isTrue()
    @Test fun adUriInOtherField() = assertThat(SpotifyAdDetector.isAd(sp(title = "x", artist = "y", album = "z", uris = listOf("spotify:ad:abc")))).isTrue()
    @Test fun advertisementFlag() = assertThat(SpotifyAdDetector.isAd(sp(title = "x", artist = "y", album = "z", flag = 1))).isTrue()
    @Test fun titleAdvertisement() = assertThat(SpotifyAdDetector.isAd(sp(title = "Advertisement", artist = "", album = ""))).isTrue()
    @Test fun titleSpotifyAd() = assertThat(SpotifyAdDetector.isAd(sp(title = "Spotify Ad", artist = "x", album = "y"))).isTrue()
    @Test fun notifTitleAdvertisement() = assertThat(SpotifyAdDetector.isAd(sp(nTitle = "Advertisement"))).isTrue()
    @Test fun shortNoSkipWhilePlaying() = assertThat(SpotifyAdDetector.isAd(sp(title = "Try Premium", artist = "Brand", album = "Brand", skip = false, dur = 30_000))).isTrue()
    @Test fun noSkipAdvertiserLike() = assertThat(SpotifyAdDetector.isAd(sp(title = "Big sale", artist = "Spotify", album = "", skip = false, dur = 120_000))).isTrue()

    // False positives that used to mute
    @Test fun adAstra() = assertThat(SpotifyAdDetector.isAd(sp(title = "Ad Astra", artist = "Max Richter", album = "Ad Astra (OST)"))).isFalse()
    @Test fun aDotD() = assertThat(SpotifyAdDetector.isAd(sp(title = "A.D.", artist = "Some Band", album = "Era"))).isFalse()
    @Test fun albumWithAD() = assertThat(SpotifyAdDetector.isAd(sp(title = "Intro", artist = "X", album = "AD 2000"))).isFalse()
    @Test fun sponsoredInTitle() = assertThat(SpotifyAdDetector.isAd(sp(title = "Sponsored by Nobody", artist = "Band", album = "LP"))).isFalse()
    @Test fun advertisementInsideLongerTitle() = assertThat(SpotifyAdDetector.isAd(sp(title = "Advertisement for Myself", artist = "Band", album = "LP"))).isFalse()
    @Test fun shortSongWithSkip() = assertThat(SpotifyAdDetector.isAd(sp(title = "Interlude", artist = "Band", album = "LP", dur = 40_000))).isFalse()
    @Test fun noSkipButPausedIsNotHeuristicAd() = assertThat(SpotifyAdDetector.isAd(sp(title = "Song", artist = "Band", album = "LP", playing = false, skip = false, dur = 30_000))).isFalse()
    @Test fun noSkipLongNormalTrack() = assertThat(SpotifyAdDetector.isAd(sp(title = "Song", artist = "Band", album = "LP", skip = false))).isFalse()
    @Test fun otherPackage() = assertThat(SpotifyAdDetector.isAd(Signals("com.example", mediaId = "spotify:ad:1"))).isFalse()
}

class MuteDeciderTest {
    @Test fun muteThenUnmuteOnContent() {
        val d = MuteDecider()
        assertThat(d.onEvaluate("ad1", 0)).isEqualTo(MuteDecider.Action.MUTE)
        assertThat(d.onEvaluate("ad1", 1000)).isEqualTo(MuteDecider.Action.NONE)
        assertThat(d.onEvaluate(null, 2000)).isEqualTo(MuteDecider.Action.UNMUTE)
        assertThat(d.newAdCount).isEqualTo(1)
    }
    @Test fun watchdogAt35sAndNoRemuteSameItem() {
        val d = MuteDecider()
        d.onEvaluate("ad1", 0)
        assertThat(d.onTick(34_999)).isEqualTo(MuteDecider.Action.NONE)
        assertThat(d.onTick(35_000)).isEqualTo(MuteDecider.Action.UNMUTE)
        assertThat(d.onEvaluate("ad1", 36_000)).isEqualTo(MuteDecider.Action.NONE)
        assertThat(d.onEvaluate("ad2", 37_000)).isEqualTo(MuteDecider.Action.MUTE)
    }
    @Test fun userUnmuteSuppressesCurrentAd() {
        val d = MuteDecider()
        d.onEvaluate("ad1", 0)
        assertThat(d.userUnmute()).isEqualTo(MuteDecider.Action.UNMUTE)
        assertThat(d.onEvaluate("ad1", 100)).isEqualTo(MuteDecider.Action.NONE)
    }
    @Test fun backToBackAdsCountedSeparately() {
        val d = MuteDecider()
        d.onEvaluate("ad1", 0); d.onEvaluate("ad2", 15_000)
        assertThat(d.newAdCount).isEqualTo(2)
        assertThat(d.muted).isTrue()
        assertThat(d.onTick(49_000)).isEqualTo(MuteDecider.Action.NONE)
    }
}
