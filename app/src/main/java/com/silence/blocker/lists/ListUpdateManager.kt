package com.silence.blocker.lists

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.silence.blocker.data.SettingsStore
import com.silence.blocker.filter.FilterCategory
import com.silence.blocker.filter.FilterEngine
import com.silence.blocker.filter.ProtectionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Downloads remote packs, caches them, and rebuilds [FilterEngine] host sets.
 */
class ListUpdateManager(
    private val appContext: Context,
    private val listRepository: ListRepository,
    private val filterEngine: FilterEngine,
    private val settingsStore: SettingsStore,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(ListUpdateUiState())
    val state: StateFlow<ListUpdateUiState> = _state.asStateFlow()

    private val _packStatuses = MutableStateFlow<Map<String, PackUiStatus>>(emptyMap())
    val packStatuses: StateFlow<Map<String, PackUiStatus>> = _packStatuses.asStateFlow()

    /** Load cached packs into the filter (no network). Call on app start. */
    suspend fun loadCachedIntoEngine() {
        mutex.withLock {
            refreshPackStatusSnapshot()
            rebuildEngineFromDisk()
        }
    }

    /**
     * Update enabled packs (or a single [packId] even if not yet enabled — still requires enable for merge).
     */
    suspend fun updateEnabledPacks(
        packId: String? = null,
        respectWifiOnly: Boolean = true,
    ): UpdateResult {
        return mutex.withLock {
            _state.value = _state.value.copy(isUpdating = true, lastError = null, lastMessage = "Updating…")
            try {
                if (respectWifiOnly && settingsStore.updateOnWifiOnly.first() && !isOnWifi()) {
                    val msg = "Wi‑Fi only is enabled and you are not on Wi‑Fi"
                    _state.value = _state.value.copy(isUpdating = false, lastError = msg, lastMessage = msg)
                    return@withLock UpdateResult(0, 0, skippedWifi = true, message = msg)
                }

                val enabled = settingsStore.enabledPackIds.first()
                val targets = if (packId != null) {
                    listOfNotNull(PackCatalog.byId(packId))
                } else {
                    PackCatalog.all.filter { it.id in enabled }
                }

                if (targets.isEmpty()) {
                    rebuildEngineFromDisk()
                    val msg = "No remote packs enabled — using built-in sample lists"
                    _state.value = _state.value.copy(isUpdating = false, lastMessage = msg)
                    return@withLock UpdateResult(0, 0, false, msg)
                }

                var ok = 0
                var fail = 0
                val errors = mutableListOf<String>()
                for (pack in targets) {
                    setPackBusy(pack.id, true)
                    val result = listRepository.downloadRaw(pack.url)
                    result.fold(
                        onSuccess = { body ->
                            val hosts = listRepository.parseText(body, pack.format)
                            if (hosts.isEmpty()) {
                                fail++
                                errors.add("${pack.displayName}: parsed 0 hosts")
                            } else {
                                listRepository.writeCache(appContext, pack.id, body, hosts.size)
                                ok++
                            }
                        },
                        onFailure = { e ->
                            fail++
                            errors.add("${pack.displayName}: ${e.message ?: "download failed"}")
                        },
                    )
                    setPackBusy(pack.id, false)
                }

                rebuildEngineFromDisk()
                refreshPackStatusSnapshot()
                settingsStore.setLastListUpdateEpochMs(System.currentTimeMillis())

                val msg = "Updated $ok pack(s)" + if (fail > 0) ", $fail failed" else ""
                _state.value = _state.value.copy(
                    isUpdating = false,
                    lastMessage = msg,
                    lastError = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    lastSuccessEpochMs = System.currentTimeMillis(),
                )
                UpdateResult(ok, fail, false, msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isUpdating = false,
                    lastError = e.message,
                    lastMessage = "Update failed",
                )
                UpdateResult(0, 1, false, e.message ?: "failed")
            }
        }
    }

    suspend fun setPackEnabled(packId: String, enabled: Boolean) {
        val current = settingsStore.enabledPackIds.first().toMutableSet()
        if (enabled) current.add(packId) else current.remove(packId)
        settingsStore.setEnabledPackIds(current)
        mutex.withLock {
            rebuildEngineFromDisk()
            refreshPackStatusSnapshot()
        }
    }

    suspend fun setCategoryEnabled(category: FilterCategory, enabled: Boolean) {
        val current = settingsStore.enabledCategories.first().toMutableSet()
        if (enabled) current.add(category.id) else current.remove(category.id)
        // Keep at least one category
        if (current.isEmpty()) current.add(FilterCategory.ADS.id)
        settingsStore.setEnabledCategories(current)
        mutex.withLock { rebuildEngineFromDisk() }
    }

    private suspend fun rebuildEngineFromDisk() {
        val enabledPacks = settingsStore.enabledPackIds.first()
        val enabledCats = settingsStore.enabledCategories.first()
            .mapNotNull { id -> FilterCategory.entries.find { it.id == id } }
            .toSet()
            .ifEmpty {
                FilterCategory.entries.filter { it.enabledByDefault }.toSet()
            }
        val profile = settingsStore.profile.first()

        val byCategory = mutableMapOf<FilterCategory, MutableSet<String>>()
        FilterCategory.entries.forEach { byCategory[it] = linkedSetOf() }

        byCategory[FilterCategory.ADS]!!.addAll(BuiltinLists.sanitize(BuiltinLists.ads))
        byCategory[FilterCategory.TRACKERS]!!.addAll(BuiltinLists.sanitize(BuiltinLists.trackers))
        byCategory[FilterCategory.MEDIA_ADS]!!.addAll(BuiltinLists.sanitize(BuiltinLists.mediaAds))
        byCategory[FilterCategory.MALWARE]!!.addAll(BuiltinLists.sanitize(BuiltinLists.malware))
        if (profile == ProtectionProfile.AGGRESSIVE) {
            byCategory[FilterCategory.MEDIA_ADS]!!.addAll(
                BuiltinLists.sanitize(BuiltinLists.mediaAdsAggressive),
            )
        }

        for (pack in PackCatalog.all) {
            if (pack.id !in enabledPacks) continue
            val hosts = listRepository.readCacheHosts(appContext, pack) ?: continue
            byCategory.getOrPut(pack.category) { linkedSetOf() }.addAll(hosts)
        }

        filterEngine.replaceCategoryHosts(
            hostsByCategory = byCategory.mapValues { it.value.toSet() },
            enabledCategories = enabledCats,
        )

        val total = enabledCats.sumOf { byCategory[it]?.size ?: 0 }
        _state.value = _state.value.copy(totalHostsLoaded = total)
    }

    private fun refreshPackStatusSnapshot() {
        val map = PackCatalog.all.associate { pack ->
            val meta = listRepository.readMeta(appContext, pack.id)
            pack.id to PackUiStatus(
                packId = pack.id,
                cached = meta != null,
                hostCount = meta?.hostCount ?: 0,
                updatedAtEpochMs = meta?.updatedAtEpochMs,
                busy = _packStatuses.value[pack.id]?.busy == true,
            )
        }
        _packStatuses.value = map
    }

    private fun setPackBusy(packId: String, busy: Boolean) {
        val cur = _packStatuses.value.toMutableMap()
        val prev = cur[packId] ?: PackUiStatus(packId)
        cur[packId] = prev.copy(busy = busy)
        _packStatuses.value = cur
    }

    private fun isOnWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

data class ListUpdateUiState(
    val isUpdating: Boolean = false,
    val lastMessage: String? = null,
    val lastError: String? = null,
    val lastSuccessEpochMs: Long? = null,
    val totalHostsLoaded: Int = 0,
)

data class PackUiStatus(
    val packId: String,
    val cached: Boolean = false,
    val hostCount: Int = 0,
    val updatedAtEpochMs: Long? = null,
    val busy: Boolean = false,
)

data class UpdateResult(
    val successCount: Int,
    val failCount: Int,
    val skippedWifi: Boolean,
    val message: String,
)
