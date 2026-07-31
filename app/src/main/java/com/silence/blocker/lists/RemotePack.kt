package com.silence.blocker.lists

import com.silence.blocker.filter.FilterCategory

/**
 * A downloadable domain/hosts pack from the catalog.
 */
data class RemotePack(
    val id: String,
    val displayName: String,
    val description: String,
    val category: FilterCategory,
    val url: String,
    /** Prefer enabling on first launch for core packs. */
    val enabledByDefault: Boolean = false,
    val format: ListFormat = ListFormat.AUTO,
)

enum class ListFormat {
    /** Detect hosts vs domain-per-line vs simple adblock ||domain^ */
    AUTO,
    HOSTS,
    DOMAINS,
    ADBLOCK_DOMAINS,
}

object PackCatalog {
    val all: List<RemotePack> = listOf(
        RemotePack(
            id = "stevenblack_unified",
            displayName = "StevenBlack unified",
            description = "Popular hosts amalgamation (ads + malware basics)",
            category = FilterCategory.ADS,
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            enabledByDefault = true,
            format = ListFormat.HOSTS,
        ),
        RemotePack(
            id = "adguard_dns_filter",
            displayName = "AdGuard DNS filter",
            description = "AdGuard Hostlists Registry DNS filter #1",
            category = FilterCategory.ADS,
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
            enabledByDefault = false,
            format = ListFormat.AUTO,
        ),
        RemotePack(
            id = "adguard_tracking",
            displayName = "AdGuard tracking protection",
            description = "Tracking protection hosts (DNS style)",
            category = FilterCategory.TRACKERS,
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_3.txt",
            enabledByDefault = true,
            format = ListFormat.AUTO,
        ),
        RemotePack(
            id = "adguard_mobile_ads",
            displayName = "AdGuard mobile ads",
            description = "Mobile ads filter (helps in-app SDKs)",
            category = FilterCategory.ADS,
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_11.txt",
            enabledByDefault = true,
            format = ListFormat.AUTO,
        ),
        RemotePack(
            id = "oisd_small",
            displayName = "OISD small",
            description = "Compact blocklist (domains). Good coverage, lower false positives.",
            category = FilterCategory.ADS,
            url = "https://small.oisd.nl/",
            enabledByDefault = false,
            format = ListFormat.DOMAINS,
        ),
        RemotePack(
            id = "urlhaus",
            displayName = "URLhaus (malware hosts)",
            description = "Abuse.ch malware-related hostnames",
            category = FilterCategory.MALWARE,
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            enabledByDefault = false,
            format = ListFormat.HOSTS,
        ),
    )

    fun byId(id: String): RemotePack? = all.find { it.id == id }

    fun defaultEnabledIds(): Set<String> =
        all.filter { it.enabledByDefault }.map { it.id }.toSet()
}
