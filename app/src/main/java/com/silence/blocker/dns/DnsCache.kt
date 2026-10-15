package com.silence.blocker.dns

import java.util.LinkedHashMap

/**
 * Small TTL-aware LRU cache for upstream DNS answers (SL-03).
 * Key = (qname lowercase, qtype). TTL clamped to [MIN_TTL_S, MAX_TTL_S]; NXDOMAIN capped at NEG_TTL_S.
 * On hit, the caller's transaction ID is written in and every RR TTL is decremented by elapsed time.
 */
class DnsCache(
    private val maxEntries: Int = 512,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private class Entry(val response: ByteArray, val storedAt: Long, val expiresAt: Long, val ttlOffsets: IntArray)

    private val map = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    @Volatile var hits = 0L; private set
    @Volatile var misses = 0L; private set

    fun key(name: String, type: Int) = name.lowercase().trimEnd('.') + "/" + type

    @Synchronized
    fun get(key: String, queryId: Int): ByteArray? {
        val e = map[key]
        val now = clock()
        if (e == null || now >= e.expiresAt) {
            if (e != null) map.remove(key)
            misses++
            return null
        }
        hits++
        val out = e.response.copyOf()
        out[0] = (queryId ushr 8).toByte()
        out[1] = queryId.toByte()
        val elapsed = ((now - e.storedAt) / 1000).toInt()
        for (off in e.ttlOffsets) {
            val ttl = readU32(out, off)
            writeU32(out, off, (ttl - elapsed).coerceAtLeast(0L))
        }
        return out
    }

    @Synchronized
    fun put(key: String, response: ByteArray) {
        val info = analyze(response) ?: return
        val rcode = response[3].toInt() and 0x0F
        val ttlS: Long = when {
            rcode == DnsPacket.RCODE_NXDOMAIN -> minOf(info.second ?: NEG_TTL_S, NEG_TTL_S)
            rcode != 0 -> return
            info.second == null -> return
            else -> info.second!!.coerceIn(MIN_TTL_S, MAX_TTL_S)
        }
        val now = clock()
        map[key] = Entry(response.copyOf(), now, now + ttlS * 1000, info.first)
    }

    @Synchronized fun flush() = map.clear()
    @Synchronized fun size() = map.size

    companion object {
        const val MIN_TTL_S = 30L
        const val MAX_TTL_S = 3600L
        const val NEG_TTL_S = 60L

        /** Returns (TTL field offsets, min TTL or null if no RRs); null if unparsable/truncated. */
        internal fun analyze(p: ByteArray): Pair<IntArray, Long?>? {
            if (p.size < 12) return null
            if ((p[2].toInt() and 0x02) != 0) return null // TC bit: don't cache
            val qd = u16(p, 4); val rr = u16(p, 6) + u16(p, 8) + u16(p, 10)
            var i = 12
            repeat(qd) { i = skipName(p, i) ?: return null; i += 4 }
            val offs = ArrayList<Int>()
            var min: Long? = null
            repeat(rr) {
                i = skipName(p, i) ?: return null
                if (i + 10 > p.size) return null
                val type = u16(p, i)
                val ttlOff = i + 4
                val rdlen = u16(p, i + 8)
                if (type != 41) { // skip OPT pseudo-RR
                    offs.add(ttlOff)
                    val t = readU32(p, ttlOff)
                    min = if (min == null) t else minOf(min!!, t)
                }
                i += 10 + rdlen
                if (i > p.size) return null
            }
            return offs.toIntArray() to min
        }

        private fun skipName(p: ByteArray, start: Int): Int? {
            var i = start
            while (i < p.size) {
                val len = p[i].toInt() and 0xFF
                if (len == 0) return i + 1
                if (len and 0xC0 == 0xC0) return if (i + 2 <= p.size) i + 2 else null
                i += 1 + len
            }
            return null
        }

        private fun u16(p: ByteArray, o: Int) = ((p[o].toInt() and 0xFF) shl 8) or (p[o + 1].toInt() and 0xFF)
        private fun readU32(p: ByteArray, o: Int): Long =
            ((p[o].toLong() and 0xFF) shl 24) or ((p[o + 1].toLong() and 0xFF) shl 16) or
                ((p[o + 2].toLong() and 0xFF) shl 8) or (p[o + 3].toLong() and 0xFF)
        private fun writeU32(p: ByteArray, o: Int, v: Long) {
            p[o] = (v ushr 24).toByte(); p[o + 1] = (v ushr 16).toByte(); p[o + 2] = (v ushr 8).toByte(); p[o + 3] = v.toByte()
        }
    }
}
