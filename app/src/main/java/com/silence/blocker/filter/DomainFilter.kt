package com.silence.blocker.filter

import com.silence.blocker.util.DomainNormalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast DNS-level domain matcher.
 *
 * Matching order for [decision]:
 * 1. User allowlist → ALLOW
 * 2. User blocklist → BLOCK
 * 3. Subscribed category host sets (exact + parent suffix) → BLOCK
 * 4. Otherwise → ALLOW (forward upstream)
 */
class DomainFilter {
    private val allowExact = ConcurrentHashMap.newKeySet<String>()
    private val blockExact = ConcurrentHashMap.newKeySet<String>()
    private val categoryHosts =
        ConcurrentHashMap<FilterCategory, MutableSet<String>>()

    @Volatile
    var enabledCategories: Set<FilterCategory> =
        FilterCategory.entries.filter { it.enabledByDefault }.toSet()

    fun clearLists() {
        categoryHosts.clear()
    }

    fun setAllowlist(domains: Collection<String>) {
        allowExact.clear()
        domains.map(DomainNormalizer::normalize).filter { it.isNotEmpty() }.forEach(allowExact::add)
    }

    fun setUserBlocklist(domains: Collection<String>) {
        blockExact.clear()
        domains.map(DomainNormalizer::normalize).filter { it.isNotEmpty() }.forEach(blockExact::add)
    }

    fun addAllow(domain: String) {
        val n = DomainNormalizer.normalize(domain)
        if (n.isNotEmpty()) allowExact.add(n)
    }

    fun addUserBlock(domain: String) {
        val n = DomainNormalizer.normalize(domain)
        if (n.isNotEmpty()) blockExact.add(n)
    }

    fun loadCategory(category: FilterCategory, domains: Collection<String>) {
        val set = ConcurrentHashMap.newKeySet<String>()
        domains.map(DomainNormalizer::normalize).filter { it.isNotEmpty() }.forEach(set::add)
        categoryHosts[category] = set
    }

    fun decision(rawHost: String): FilterDecision {
        val host = DomainNormalizer.normalize(rawHost)
        if (host.isEmpty() || looksLikeIp(host)) {
            return FilterDecision(FilterAction.ALLOW, host, reason = "ip_or_empty")
        }

        if (matchesSet(host, allowExact)) {
            return FilterDecision(FilterAction.ALLOW, host, reason = "user_allow")
        }
        if (matchesSet(host, blockExact)) {
            return FilterDecision(FilterAction.BLOCK, host, reason = "user_block", category = null)
        }

        for (category in enabledCategories) {
            val set = categoryHosts[category] ?: continue
            if (matchesSet(host, set)) {
                return FilterDecision(FilterAction.BLOCK, host, reason = "list", category = category)
            }
        }

        return FilterDecision(FilterAction.ALLOW, host, reason = "default")
    }

    fun shouldBlock(rawHost: String): Boolean = decision(rawHost).action == FilterAction.BLOCK

    fun listSize(category: FilterCategory): Int = categoryHosts[category]?.size ?: 0

    fun totalListHosts(): Int = categoryHosts.values.sumOf { it.size }

    private fun matchesSet(host: String, set: Set<String>): Boolean {
        if (host in set) return true
        // Walk parent domains: ads.foo.example.com → foo.example.com → example.com
        var idx = host.indexOf('.')
        while (idx > 0 && idx < host.lastIndex) {
            val parent = host.substring(idx + 1)
            if (parent in set) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    private fun looksLikeIp(host: String): Boolean {
        // Cheap IPv4 check; IPv6 contains ':'
        if (host.contains(':')) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }
}

enum class FilterAction {
    ALLOW,
    BLOCK,
}

data class FilterDecision(
    val action: FilterAction,
    val host: String,
    val reason: String,
    val category: FilterCategory? = null,
)
