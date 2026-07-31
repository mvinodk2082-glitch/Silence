package com.silence.blocker.util

/**
 * Normalizes hostnames for filter lookups.
 * Strips trailing dots, lowercases, strips common "www." for exact-set convenience
 * only when matching — callers that need full FQDN should use [normalize] alone.
 */
object DomainNormalizer {
    fun normalize(raw: String): String {
        var host = raw.trim().lowercase()
        if (host.endsWith('.')) {
            host = host.dropLast(1)
        }
        // Strip brackets from IPv6 literals if someone passes them by mistake.
        if (host.startsWith('[') && host.endsWith(']')) {
            host = host.substring(1, host.length - 1)
        }
        return host
    }

    /** True if [host] is equal to [rule] or a subdomain of [rule]. */
    fun matchesHostOrSuffix(host: String, rule: String): Boolean {
        val h = normalize(host)
        val r = normalize(rule)
        if (h.isEmpty() || r.isEmpty()) return false
        if (h == r) return true
        return h.endsWith(".$r")
    }
}
