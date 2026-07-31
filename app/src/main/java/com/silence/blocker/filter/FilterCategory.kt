package com.silence.blocker.filter

enum class FilterCategory(
    val id: String,
    val displayName: String,
    val description: String,
    val enabledByDefault: Boolean,
) {
    ADS(
        id = "ads",
        displayName = "Ads",
        description = "Common display and mobile ad networks",
        enabledByDefault = true,
    ),
    TRACKERS(
        id = "trackers",
        displayName = "Trackers",
        description = "Analytics and tracking beacons",
        enabledByDefault = true,
    ),
    MEDIA_ADS(
        id = "media_ads",
        displayName = "Media ads (audio/video)",
        description = "VAST, video ad SDKs, and known audio/video ad hosts",
        enabledByDefault = true,
    ),
    MALWARE(
        id = "malware",
        displayName = "Malware / phishing",
        description = "Optional security hosts (off by default in scaffold sample)",
        enabledByDefault = false,
    ),
}

enum class ProtectionProfile(
    val id: String,
    val displayName: String,
    val description: String,
) {
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        description = "Ads + trackers + media ad domains. Good default.",
    ),
    AGGRESSIVE(
        id = "aggressive",
        displayName = "Aggressive",
        description = "More media and edge ad hosts. Higher breakage risk.",
    ),
    PRIVACY(
        id = "privacy",
        displayName = "Privacy",
        description = "Maximize tracker blocking; ads still on.",
    ),
}
