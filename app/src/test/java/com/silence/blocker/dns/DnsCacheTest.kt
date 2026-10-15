package com.silence.blocker.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DnsCacheTest {
    // Response for example.com A with one answer, TTL given, rcode given
    private fun response(id: Int, ttl: Long, rcode: Int = 0, answers: Int = 1): ByteArray {
        val name = byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0)
        val hdr = byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x81.toByte(), (0x80 or rcode).toByte(), 0, 1, 0, answers.toByte(), 0, 0, 0, 0)
        val q = name + byteArrayOf(0, 1, 0, 1)
        val rr = if (answers == 0) ByteArray(0) else byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1,
            (ttl ushr 24).toByte(), (ttl ushr 16).toByte(), (ttl ushr 8).toByte(), ttl.toByte(), 0, 4, 1, 2, 3, 4)
        return hdr + q + rr
    }
    private fun ttlOf(p: ByteArray): Long { val o = p.size - 10; return ((p[o].toLong() and 0xFF) shl 24) or ((p[o+1].toLong() and 0xFF) shl 16) or ((p[o+2].toLong() and 0xFF) shl 8) or (p[o+3].toLong() and 0xFF) }

    @Test fun hitRewritesIdAndDecrementsTtl() {
        var now = 0L
        val c = DnsCache(clock = { now })
        val k = c.key("Example.com.", 1)
        c.put(k, response(0x1111, 300))
        now = 10_000
        val hit = c.get(k, 0xABCD)!!
        assertThat(hit[0].toInt() and 0xFF).isEqualTo(0xAB)
        assertThat(hit[1].toInt() and 0xFF).isEqualTo(0xCD)
        assertThat(ttlOf(hit)).isEqualTo(290)
    }

    @Test fun ttlClampedToMinimum30s() {
        var now = 0L
        val c = DnsCache(clock = { now })
        c.put("k", response(1, 5)); now = 29_000
        assertThat(c.get("k", 1)).isNotNull(); now = 30_000
        assertThat(c.get("k", 1)).isNull()
    }

    @Test fun ttlClampedToOneHour() {
        var now = 0L
        val c = DnsCache(clock = { now })
        c.put("k", response(1, 86_400)); now = 3_600_000
        assertThat(c.get("k", 1)).isNull()
    }

    @Test fun nxdomainCappedAt60s() {
        var now = 0L
        val c = DnsCache(clock = { now })
        c.put("k", response(1, 0, rcode = 3, answers = 0)); now = 59_000
        assertThat(c.get("k", 1)).isNotNull(); now = 60_000
        assertThat(c.get("k", 1)).isNull()
    }

    @Test fun servfailNotCachedAndLruEvicts() {
        val c = DnsCache(maxEntries = 2)
        c.put("s", response(1, 300, rcode = 2)); assertThat(c.size()).isEqualTo(0)
        c.put("a", response(1, 300)); c.put("b", response(1, 300)); c.put("c", response(1, 300))
        assertThat(c.get("a", 1)).isNull(); assertThat(c.size()).isEqualTo(2)
        c.flush(); assertThat(c.size()).isEqualTo(0)
    }
}
