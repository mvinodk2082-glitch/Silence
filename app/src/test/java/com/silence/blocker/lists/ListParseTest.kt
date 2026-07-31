package com.silence.blocker.lists

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListParseTest {
    private val repo = ListRepository()

    @Test
    fun parsesHostsFile() {
        val text = """
            # comment
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.example.com # inline
            example.com
        """.trimIndent()
        val hosts = repo.parseHostsFile(text)
        assertThat(hosts).containsAtLeast("ads.example.com", "tracker.example.com", "example.com")
        assertThat(hosts).doesNotContain("localhost")
    }

    @Test
    fun parsesAdblockDomains() {
        val text = """
            ! Title: test
            ||doubleclick.net^
            ||ads.example.com^${'$'}third-party
            @@||good.example.com^
            ##.ad-banner
            /regexrule/
        """.trimIndent()
        val hosts = repo.parseAdblockDomains(text)
        assertThat(hosts).contains("doubleclick.net")
        assertThat(hosts).contains("ads.example.com")
        assertThat(hosts).doesNotContain("good.example.com")
    }

    @Test
    fun parseAutoDetectsHosts() {
        val text = """
            0.0.0.0 a.example.com
            0.0.0.0 b.example.com
            0.0.0.0 c.example.com
            0.0.0.0 d.example.com
        """.trimIndent()
        val hosts = repo.parseAuto(text)
        assertThat(hosts).contains("a.example.com")
        assertThat(hosts).hasSize(4)
    }

    @Test
    fun parseAutoDetectsAdblock() {
        val text = """
            ||one.example.com^
            ||two.example.com^
            ||three.example.com^
            ||four.example.com^
        """.trimIndent()
        val hosts = repo.parseAuto(text)
        assertThat(hosts).containsAtLeast("one.example.com", "two.example.com")
    }

    @Test
    fun catalogHasDefaultEnabled() {
        assertThat(PackCatalog.defaultEnabledIds()).isNotEmpty()
        assertThat(PackCatalog.byId("stevenblack_unified")).isNotNull()
    }
}
