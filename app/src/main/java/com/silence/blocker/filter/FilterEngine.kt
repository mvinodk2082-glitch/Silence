package com.silence.blocker.filter

import com.silence.blocker.lists.BuiltinLists
import com.silence.blocker.lists.ListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns [DomainFilter] plus live stats. Thread-safe for VPN packet path.
 *
 * Phase 1: throttled UI publish, day rollover, hydrate from disk.
 */
class FilterEngine(
    private val listRepository: ListRepository = ListRepository(),
) {
    val filter = DomainFilter()

    private val blockedToday = AtomicLong(0)
    private val allowedToday = AtomicLong(0)
    private val _snapshot = MutableStateFlow(StatsSnapshot())
    val snapshot: StateFlow<StatsSnapshot> = _snapshot.asStateFlow()

    private val recentBlocked = ArrayDeque<String>(64)
    private val allowPublishCounter = AtomicLong(0)

    @Volatile
    private var dayStamp: String = LocalDate.now().toString()

    init {
        reloadFromBuiltin()
    }

    fun reloadFromBuiltin() {
        replaceCategoryHosts(
            hostsByCategory = mapOf(
                FilterCategory.ADS to BuiltinLists.sanitize(BuiltinLists.ads),
                FilterCategory.TRACKERS to BuiltinLists.sanitize(BuiltinLists.trackers),
                FilterCategory.MEDIA_ADS to BuiltinLists.sanitize(BuiltinLists.mediaAds),
                FilterCategory.MALWARE to BuiltinLists.sanitize(BuiltinLists.malware),
            ),
            enabledCategories = FilterCategory.entries.filter { it.enabledByDefault }.toSet(),
        )
    }

    /**
     * Replace all category host sets (built-in + remote merges done by ListUpdateManager).
     */
    fun replaceCategoryHosts(
        hostsByCategory: Map<FilterCategory, Set<String>>,
        enabledCategories: Set<FilterCategory>,
    ) {
        filter.clearLists()
        for ((category, hosts) in hostsByCategory) {
            filter.loadCategory(category, hosts)
        }
        filter.enabledCategories = enabledCategories.ifEmpty {
            FilterCategory.entries.filter { it.enabledByDefault }.toSet()
        }
        publish()
    }

    fun applyUserRules(allow: Collection<String>, block: Collection<String>) {
        filter.setAllowlist(allow)
        filter.setUserBlocklist(block)
    }

    /**
     * Profile still adjusts default categories; host merge is owned by ListUpdateManager.
     * Kept for Settings profile changes before a full rebuild.
     */
    fun applyProfile(profile: ProtectionProfile) {
        val cats = when (profile) {
            ProtectionProfile.BALANCED -> setOf(
                FilterCategory.ADS,
                FilterCategory.TRACKERS,
                FilterCategory.MEDIA_ADS,
            )
            ProtectionProfile.AGGRESSIVE -> setOf(
                FilterCategory.ADS,
                FilterCategory.TRACKERS,
                FilterCategory.MEDIA_ADS,
            )
            ProtectionProfile.PRIVACY -> setOf(
                FilterCategory.ADS,
                FilterCategory.TRACKERS,
                FilterCategory.MEDIA_ADS,
            )
        }
        filter.enabledCategories = cats
        // Ensure aggressive media extras are present if media list is only built-in sized —
        // full rebuild via ListUpdateManager is preferred after profile change.
        if (profile == ProtectionProfile.AGGRESSIVE) {
            val current = filter.listSize(FilterCategory.MEDIA_ADS)
            // Reload media with aggressive extras when still on built-in-only scale
            if (current <= BuiltinLists.mediaAds.size + 5) {
                filter.loadCategory(
                    FilterCategory.MEDIA_ADS,
                    BuiltinLists.sanitize(BuiltinLists.mediaAds + BuiltinLists.mediaAdsAggressive),
                )
            }
        }
        publish()
    }

    /** Restore counters after process restart (same calendar day only). */
    fun hydrateCounters(blocked: Long, allowed: Long, day: String = LocalDate.now().toString()) {
        val today = LocalDate.now().toString()
        dayStamp = today
        if (day == today) {
            blockedToday.set(blocked.coerceAtLeast(0))
            allowedToday.set(allowed.coerceAtLeast(0))
        } else {
            blockedToday.set(0)
            allowedToday.set(0)
        }
        publish()
    }

    fun decide(host: String): FilterDecision {
        maybeRollDay()
        val decision = filter.decision(host)
        when (decision.action) {
            FilterAction.BLOCK -> {
                blockedToday.incrementAndGet()
                synchronized(recentBlocked) {
                    if (recentBlocked.size >= 50) recentBlocked.removeFirst()
                    recentBlocked.addLast(decision.host)
                }
                publish()
            }
            FilterAction.ALLOW -> {
                allowedToday.incrementAndGet()
                // Throttle UI updates for high-QPS allow path
                if (allowPublishCounter.incrementAndGet() % 25L == 0L) {
                    publish()
                }
            }
        }
        return decision
    }

    fun blockedCountToday(): Long = blockedToday.get()
    fun allowedCountToday(): Long = allowedToday.get()

    fun snapshotNow(): StatsSnapshot = _snapshot.value

    fun resetDailyCounters() {
        blockedToday.set(0)
        allowedToday.set(0)
        allowPublishCounter.set(0)
        synchronized(recentBlocked) { recentBlocked.clear() }
        dayStamp = LocalDate.now().toString()
        publish()
    }

    fun forcePublish() = publish()

    private fun maybeRollDay() {
        val today = LocalDate.now().toString()
        if (today != dayStamp) {
            dayStamp = today
            blockedToday.set(0)
            allowedToday.set(0)
            allowPublishCounter.set(0)
            synchronized(recentBlocked) { recentBlocked.clear() }
            publish()
        }
    }

    private fun publish() {
        val recent = synchronized(recentBlocked) { recentBlocked.toList().asReversed() }
        _snapshot.value = StatsSnapshot(
            blockedToday = blockedToday.get(),
            allowedToday = allowedToday.get(),
            listHosts = filter.totalListHosts(),
            recentBlocked = recent,
            day = dayStamp,
        )
    }
}

data class StatsSnapshot(
    val blockedToday: Long = 0,
    val allowedToday: Long = 0,
    val listHosts: Int = 0,
    val recentBlocked: List<String> = emptyList(),
    val day: String = LocalDate.now().toString(),
)
