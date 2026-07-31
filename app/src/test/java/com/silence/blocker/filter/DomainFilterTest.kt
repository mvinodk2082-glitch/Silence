package com.silence.blocker.filter

import com.google.common.truth.Truth.assertThat
import com.silence.blocker.lists.BuiltinLists
import org.junit.Test

class DomainFilterTest {
    private fun filterWithBuiltin(): DomainFilter {
        val f = DomainFilter()
        f.loadCategory(FilterCategory.ADS, BuiltinLists.ads)
        f.loadCategory(FilterCategory.TRACKERS, BuiltinLists.trackers)
        f.loadCategory(FilterCategory.MEDIA_ADS, BuiltinLists.mediaAds)
        f.enabledCategories = setOf(
            FilterCategory.ADS,
            FilterCategory.TRACKERS,
            FilterCategory.MEDIA_ADS,
        )
        return f
    }

    @Test
    fun blocksExactAdDomain() {
        val f = filterWithBuiltin()
        assertThat(f.shouldBlock("doubleclick.net")).isTrue()
        assertThat(f.shouldBlock("pagead2.googlesyndication.com")).isTrue()
    }

    @Test
    fun blocksSubdomainOfListedHost() {
        val f = filterWithBuiltin()
        assertThat(f.shouldBlock("stats.doubleclick.net")).isTrue()
        assertThat(f.shouldBlock("foo.bar.adnxs.com")).isTrue()
    }

    @Test
    fun allowsNormalSites() {
        val f = filterWithBuiltin()
        assertThat(f.shouldBlock("example.com")).isFalse()
        assertThat(f.shouldBlock("wikipedia.org")).isFalse()
        assertThat(f.shouldBlock("github.com")).isFalse()
    }

    @Test
    fun allowlistOverridesBlock() {
        val f = filterWithBuiltin()
        f.setAllowlist(listOf("doubleclick.net"))
        assertThat(f.shouldBlock("doubleclick.net")).isFalse()
        assertThat(f.shouldBlock("ads.doubleclick.net")).isFalse()
    }

    @Test
    fun userBlocklistWins() {
        val f = filterWithBuiltin()
        f.setUserBlocklist(listOf("evil.example"))
        assertThat(f.shouldBlock("evil.example")).isTrue()
        assertThat(f.shouldBlock("a.evil.example")).isTrue()
    }

    @Test
    fun mediaAdsCategoryBlocksVideoAdHosts() {
        val f = filterWithBuiltin()
        assertThat(f.shouldBlock("imasdk.googleapis.com")).isTrue()
        assertThat(f.shouldBlock("pubads.g.doubleclick.net")).isTrue()
        assertThat(f.shouldBlock("audio.adswizz.com")).isTrue()
    }

    @Test
    fun disabledCategoryDoesNotBlock() {
        val f = filterWithBuiltin()
        f.enabledCategories = setOf(FilterCategory.ADS)
        // trackers only in TRACKERS set
        assertThat(f.shouldBlock("google-analytics.com")).isFalse()
        assertThat(f.shouldBlock("doubleclick.net")).isTrue()
    }
}

