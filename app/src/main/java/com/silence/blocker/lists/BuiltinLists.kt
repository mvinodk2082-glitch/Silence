package com.silence.blocker.lists

/**
 * Offline-first sample hosts so Silence works before remote list download.
 * Not exhaustive — Phase 2 wires EasyList/AdGuard-style updates.
 *
 * Media ads: domains commonly used by VAST / mobile video / audio ad SDKs.
 * Same-origin SSAI (e.g. some YouTube paths) cannot be blocked at DNS alone.
 */
object BuiltinLists {
    val ads: Set<String> = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "adform.net",
        "adcolony.com",
        "unityads.unity3d.com",
        "ads.mopub.com",
        "mopub.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "ad.doubleclick.net",
        "static.ads-twitter.com",
        "ads-api.twitter.com",
        "an.facebook.com",
        "ads.linkedin.com",
        "amazon-adsystem.com",
        "media.net",
        "casalemedia.com",
        "bidswitch.net",
        "smartadserver.com",
        "adsafeprotected.com",
        "moatads.com",
        "scorecardresearch.com",
    )

    val trackers: Set<String> = setOf(
        "google-analytics.com",
        "ssl.google-analytics.com",
        "www.google-analytics.com",
        "googletagmanager.com",
        "www.googletagmanager.com",
        "segment.io",
        "api.segment.io",
        "mixpanel.com",
        "api.mixpanel.com",
        "amplitude.com",
        "api2.amplitude.com",
        "hotjar.com",
        "static.hotjar.com",
        "fullstory.com",
        "clarity.ms",
        "bat.bing.com",
        "analytics.twitter.com",
        "pixel.facebook.com",
        "connect.facebook.net",
        "tr.snapchat.com",
        "ads.yahoo.com",
        "sp.analytics.yahoo.com",
    )

    val mediaAds: Set<String> = setOf(
        // Mobile / video ad SDKs and VAST-related hosts
        "imasdk.googleapis.com",
        "googleads.g.doubleclick.net",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "pagead2.googleadservices.com",
        "ads.youtube.com",
        "s0.2mdn.net",
        "s1.2mdn.net",
        "ad.youtube.com",
        "vungle.com",
        "api.vungle.com",
        "ads.vungle.com",
        "applovin.com",
        "rt.applovin.com",
        "ms.applovin.com",
        "unityads.unity3d.com",
        "auction.unityads.unity3d.com",
        "supersonicads.com",
        "ironsrc.com",
        "video.unrulymedia.com",
        "ads.stickyadstv.com",
        "vast.adsafeprotected.com",
        "fwmrm.net",
        "freewheel.tv",
        "adserver.adtechus.com",
        "live.chartboost.com",
        "chartboost.com",
        "teads.tv",
        "a.teads.tv",
        "spotxchange.com",
        "search.spotxchange.com",
        "springserve.com",
        "tremorhub.com",
        "ads.w55c.net",
        "innovid.com",
        "s.innovid.com",
        // Audio-adjacent ad tech
        "audio.adswizz.com",
        "adswizz.com",
        "ads.spotify.com",
    )

    /** Extra media hosts for Aggressive profile — higher breakage risk. */
    val mediaAdsAggressive: Set<String> = setOf(
        "pagead.l.doubleclick.net",
        "gads.pubmatic.com",
        "ads.stickyadstv.com",
        "cdn.adsafeprotected.com",
        "pixel.adsafeprotected.com",
        "serving-sys.com",
        "bs.serving-sys.com",
        "ad.smaato.net",
        "sdk.api.smaato.net",
        "ads.flurry.com",
        "data.flurry.com",
    )

    val malware: Set<String> = setOf(
        // Placeholder sample only — real malware lists come from remote packs
        "malware-example.invalid",
    )

    /** Domains that appear as path-like mistakes in comments — filter host-only entries. */
    fun sanitize(domains: Set<String>): Set<String> =
        domains.filter { !it.contains('/') && it.isNotBlank() }.toSet()
}
