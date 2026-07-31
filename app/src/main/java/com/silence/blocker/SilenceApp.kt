package com.silence.blocker

import android.app.Application
import com.silence.blocker.data.SettingsStore
import com.silence.blocker.data.StatsStore
import com.silence.blocker.data.UserRulesStore
import com.silence.blocker.filter.FilterEngine
import com.silence.blocker.lists.ListRepository
import com.silence.blocker.lists.ListUpdateManager
import com.silence.blocker.lists.scheduleListUpdates
import com.silence.blocker.media.MediaAdGuard
import com.silence.blocker.vpn.PrivateDnsMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SilenceApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var filterEngine: FilterEngine
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var userRulesStore: UserRulesStore
        private set
    lateinit var listRepository: ListRepository
        private set
    lateinit var statsStore: StatsStore
        private set
    lateinit var listUpdateManager: ListUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        listRepository = ListRepository()
        filterEngine = FilterEngine(listRepository)
        settingsStore = SettingsStore(this)
        userRulesStore = UserRulesStore(settingsStore)
        statsStore = StatsStore(this)
        listUpdateManager = ListUpdateManager(
            appContext = this,
            listRepository = listRepository,
            filterEngine = filterEngine,
            settingsStore = settingsStore,
        )

        appScope.launch {
            settingsStore.ensurePackDefaults()
            val saved = statsStore.loadToday()
            filterEngine.hydrateCounters(saved.blocked, saved.allowed, saved.day)
            filterEngine.applyUserRules(
                allow = settingsStore.allowlist.first(),
                block = settingsStore.blocklist.first(),
            )
            listUpdateManager.loadCachedIntoEngine()
            PrivateDnsMonitor.refresh(this@SilenceApp)
            scheduleListUpdates(this@SilenceApp, settingsStore)
            MediaAdGuard.startIfEnabled(this@SilenceApp)

            // First install: if default packs have no cache, try one update (wifi-only respected)
            val statuses = listUpdateManager.packStatuses.value
            val enabled = settingsStore.enabledPackIds.first()
            val missing = enabled.any { id -> statuses[id]?.cached != true }
            if (missing) {
                listUpdateManager.updateEnabledPacks(respectWifiOnly = true)
            }
        }
    }
}
