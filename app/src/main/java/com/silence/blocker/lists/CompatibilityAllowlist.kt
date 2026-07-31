package com.silence.blocker.lists

/**
 * Domains that must never be sinkholed — blocking them makes major apps
 * show "offline" or fail login even when only ad hosts were intended.
 *
 * Applied on top of user allowlist (user can still add more).
 * Parent match applies (e.g. allow `spotify.com` covers `spclient.wg.spotify.com`).
 */
object CompatibilityAllowlist {
    /**
     * Core Spotify delivery / API hosts.
     * Note: allowlisting `spotify.com` also allows `ads.spotify.com` (prefer working app).
     */
    val domains: Set<String> = setOf(
        "spotify.com",
        "scdn.co",
        "spotifycdn.com",
        "spotifycdn.net",
        "pscdn.co",
        "spotilocal.com",
        "spotify.map.fastly.net",
        "audio-ak-spotify-com.akamaized.net",
        "heads-ak-spotify-com.akamaized.net",
        "spotify-com.akamaized.net",
        // Related CDNs used in some regions
        "byspotify.com",
        "spotifycharts.com",
    )

    /** Package names that often refuse to work under *any* VPN interface. */
    val suggestedVpnExcludes: Map<String, String> = mapOf(
        "com.spotify.music" to "Spotify",
        "com.spotify.lite" to "Spotify Lite",
        "com.netflix.mediaclient" to "Netflix",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.amazon.mp3" to "Amazon Music",
        "com.aspiro.tidal" to "Tidal",
    )
}
