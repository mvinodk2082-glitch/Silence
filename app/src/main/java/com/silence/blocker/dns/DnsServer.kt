package com.silence.blocker.dns

import com.silence.blocker.filter.FilterAction
import com.silence.blocker.filter.FilterEngine
import java.net.DatagramSocket
import java.net.Socket

/**
 * Decides block vs forward for a raw DNS UDP payload.
 */
class DnsServer(
    private val filterEngine: FilterEngine,
    private val upstream: UpstreamResolver = UpstreamResolver(),
) {
    fun setUpstream(host: String) {
        upstream.setUpstream(host)
    }

    val cache = DnsCache()
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<ByteArray?>>()

    /** Drop cached answers (call on network change). */
    fun flushCache() = cache.flush()

    fun upstreamStats(): UpstreamStats = upstream.stats()

    /**
     * @return response bytes, or a SERVFAIL-style empty-ish response when upstream dies
     *         (we prefer NXDOMAIN-like SERVFAIL via [DnsPacket.buildServFail] so clients retry less wildly)
     */
    fun handleQuery(
        query: ByteArray,
        length: Int,
        protectUDP: (DatagramSocket) -> Boolean,
        protectTCP: (Socket) -> Boolean,
    ): ByteArray? {
        val question = DnsPacket.parseQuestion(query, length) ?: return null
        val decision = filterEngine.decide(question.name)
        return when (decision.action) {
            FilterAction.BLOCK -> {
                when (question.type) {
                    DnsPacket.TYPE_A, DnsPacket.TYPE_AAAA ->
                        DnsPacket.buildSinkholeResponse(query, length, question)
                    else -> DnsPacket.buildNxDomain(query, length, question)
                }
            }
            FilterAction.ALLOW -> {
                resolveCached(query, length, question) { upstream.resolve(query, length, protectUDP, protectTCP) }
                    ?: DnsPacket.buildServFail(query, length, question)
            }
        }
    }

    /** Cache lookup, then coalesce identical in-flight upstream lookups into one call. */
    internal fun resolveCached(
        query: ByteArray,
        length: Int,
        question: DnsPacket.Question,
        fetch: () -> ByteArray?,
    ): ByteArray? {
        val key = cache.key(question.name, question.type)
        cache.get(key, question.headerId)?.let { return it }
        val mine = java.util.concurrent.CompletableFuture<ByteArray?>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) {
            val r = try { existing.get(5, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) { null }
            return r?.copyOf()?.also { it[0] = (question.headerId ushr 8).toByte(); it[1] = question.headerId.toByte() }
        }
        val result = try { fetch() } catch (_: Exception) { null }
        if (result != null) cache.put(key, result)
        inFlight.remove(key)
        mine.complete(result)
        return result
    }
}
