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
                upstream.resolve(query, length, protectUDP, protectTCP)
                    ?: DnsPacket.buildServFail(query, length, question)
            }
        }
    }
}
