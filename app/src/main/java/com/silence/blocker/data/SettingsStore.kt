package com.silence.blocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.silence.blocker.filter.FilterCategory
import com.silence.blocker.filter.ProtectionProfile
import com.silence.blocker.lists.PackCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silence_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val protectionEnabled = booleanPreferencesKey("protection_enabled")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
        val upstreamDns = stringPreferencesKey("upstream_dns")
        val profile = stringPreferencesKey("profile")
        val allowlist = stringSetPreferencesKey("allowlist")
        val blocklist = stringSetPreferencesKey("blocklist")
        val excludedPackages = stringSetPreferencesKey("excluded_packages")
        val mediaAdsEnabled = booleanPreferencesKey("media_ads_enabled")
        val enabledPackIds = stringSetPreferencesKey("enabled_pack_ids")
        val enabledCategories = stringSetPreferencesKey("enabled_categories")
        val autoUpdateLists = booleanPreferencesKey("auto_update_lists")
        val updateOnWifiOnly = booleanPreferencesKey("update_on_wifi_only")
        val lastListUpdateEpochMs = longPreferencesKey("last_list_update_epoch_ms")
        val packsInitialized = booleanPreferencesKey("packs_initialized")
        val powerSaverMode = booleanPreferencesKey("power_saver_mode")
        val alwaysOnConfirmed = booleanPreferencesKey("always_on_confirmed")
        val batteryTipDismissed = booleanPreferencesKey("battery_tip_dismissed")
        val spotifyAdMuteEnabled = booleanPreferencesKey("spotify_ad_mute_enabled")
    }

    val protectionEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.protectionEnabled] ?: false
    }

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.startOnBoot] ?: false
    }

    val upstreamDns: Flow<String> = context.dataStore.data.map {
        it[Keys.upstreamDns] ?: "1.1.1.1"
    }

    val profile: Flow<ProtectionProfile> = context.dataStore.data.map { prefs ->
        ProtectionProfile.entries.find { it.id == prefs[Keys.profile] }
            ?: ProtectionProfile.BALANCED
    }

    val allowlist: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.allowlist] ?: emptySet()
    }

    val blocklist: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.blocklist] ?: emptySet()
    }

    val excludedPackages: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.excludedPackages] ?: emptySet()
    }

    val mediaAdsEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.mediaAdsEnabled] ?: true
    }

    val enabledPackIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.enabledPackIds] ?: PackCatalog.defaultEnabledIds()
    }

    val enabledCategories: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.enabledCategories]
            ?: FilterCategory.entries.filter { it.enabledByDefault }.map { it.id }.toSet()
    }

    val autoUpdateLists: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.autoUpdateLists] ?: true
    }

    val updateOnWifiOnly: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.updateOnWifiOnly] ?: true
    }

    val lastListUpdateEpochMs: Flow<Long?> = context.dataStore.data.map {
        it[Keys.lastListUpdateEpochMs]
    }

    /** Longer flush intervals / lower UI churn when true. */
    val powerSaverMode: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.powerSaverMode] ?: false
    }

    val alwaysOnConfirmed: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.alwaysOnConfirmed] ?: false
    }

    val batteryTipDismissed: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.batteryTipDismissed] ?: false
    }

    val spotifyAdMuteEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.spotifyAdMuteEnabled] ?: false
    }

    suspend fun setProtectionEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.protectionEnabled] = value }
    }

    suspend fun setStartOnBoot(value: Boolean) {
        context.dataStore.edit { it[Keys.startOnBoot] = value }
    }

    suspend fun setUpstreamDns(value: String) {
        context.dataStore.edit { it[Keys.upstreamDns] = value }
    }

    suspend fun setProfile(profile: ProtectionProfile) {
        context.dataStore.edit { it[Keys.profile] = profile.id }
    }

    suspend fun setAllowlist(domains: Set<String>) {
        context.dataStore.edit { it[Keys.allowlist] = domains }
    }

    suspend fun setBlocklist(domains: Set<String>) {
        context.dataStore.edit { it[Keys.blocklist] = domains }
    }

    suspend fun setExcludedPackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.excludedPackages] = packages }
    }

    suspend fun setMediaAdsEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.mediaAdsEnabled] = value }
    }

    suspend fun setEnabledPackIds(ids: Set<String>) {
        context.dataStore.edit {
            it[Keys.enabledPackIds] = ids
            it[Keys.packsInitialized] = true
        }
    }

    suspend fun setEnabledCategories(ids: Set<String>) {
        context.dataStore.edit { it[Keys.enabledCategories] = ids }
    }

    suspend fun setAutoUpdateLists(value: Boolean) {
        context.dataStore.edit { it[Keys.autoUpdateLists] = value }
    }

    suspend fun setUpdateOnWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.updateOnWifiOnly] = value }
    }

    suspend fun setLastListUpdateEpochMs(value: Long) {
        context.dataStore.edit { it[Keys.lastListUpdateEpochMs] = value }
    }

    suspend fun setPowerSaverMode(value: Boolean) {
        context.dataStore.edit { it[Keys.powerSaverMode] = value }
    }

    suspend fun setAlwaysOnConfirmed(value: Boolean) {
        context.dataStore.edit { it[Keys.alwaysOnConfirmed] = value }
    }

    suspend fun setBatteryTipDismissed(value: Boolean) {
        context.dataStore.edit { it[Keys.batteryTipDismissed] = value }
    }

    suspend fun setSpotifyAdMuteEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.spotifyAdMuteEnabled] = value }
    }

    /** First-run: seed default pack selection once. */
    suspend fun ensurePackDefaults() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.packsInitialized] != true) {
                prefs[Keys.enabledPackIds] = PackCatalog.defaultEnabledIds()
                prefs[Keys.enabledCategories] =
                    FilterCategory.entries.filter { it.enabledByDefault }.map { it.id }.toSet()
                prefs[Keys.packsInitialized] = true
            }
        }
    }
}
