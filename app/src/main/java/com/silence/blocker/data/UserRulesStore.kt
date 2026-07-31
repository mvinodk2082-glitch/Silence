package com.silence.blocker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Thin façade over allow/block lists in [SettingsStore].
 * Kept separate so UI can grow import/export without bloating settings.
 */
class UserRulesStore(private val settings: SettingsStore) {
    val allowlist: Flow<Set<String>> = settings.allowlist
    val blocklist: Flow<Set<String>> = settings.blocklist

    suspend fun allow(domain: String) {
        val current = settings.allowlist.first().toMutableSet()
        current.add(domain.lowercase().trim())
        settings.setAllowlist(current)
    }

    suspend fun block(domain: String) {
        val current = settings.blocklist.first().toMutableSet()
        current.add(domain.lowercase().trim())
        settings.setBlocklist(current)
    }

    suspend fun removeAllow(domain: String) {
        val current = settings.allowlist.first().toMutableSet()
        current.remove(domain.lowercase().trim())
        settings.setAllowlist(current)
    }

    suspend fun removeBlock(domain: String) {
        val current = settings.blocklist.first().toMutableSet()
        current.remove(domain.lowercase().trim())
        settings.setBlocklist(current)
    }
}
