package com.silence.blocker.dns

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Forwards DNS queries to upstream resolvers on sockets protected by [VpnService.protect].
 *
 * Hardening:
 * - Primary + fallback upstreams
 * - UDP retries with short timeout
 * - TCP fallback when UDP fails or response is truncated (TC bit)
 */
class UpstreamResolver(
    private var primaryHost: String = "1.1.1.1",
    private var primaryPort: Int = 53,
    private val fallbackHosts: List<String> = listOf("9.9.9.9", "8.8.8.8"),
    private val udpTimeoutMs: Int = 1_500,
    private val tcpTimeoutMs: Int = 2_500,
    private val udpAttemptsPerHost: Int = 2,
) {
    private val successCount = AtomicLong(0)
    private val failureCount = AtomicLong(0)
    private val tcpFallbackCount = AtomicLong(0)

    fun setUpstream(host: String, port: Int = 53) {
        primaryHost = host.trim().ifEmpty { "1.1.1.1" }
        primaryPort = port
    }

    fun stats(): UpstreamStats = UpstreamStats(
        success = successCount.get(),
        failure = failureCount.get(),
        tcpFallbacks = tcpFallbackCount.get(),
        primary = primaryHost,
    )

    /**
     * @param protectUDP protect a [DatagramSocket]
     * @param protectTCP protect a [Socket] (TCP DNS)
     */
    fun resolve(
        query: ByteArray,
        queryLen: Int,
        protectUDP: (DatagramSocket) -> Boolean,
        protectTCP: (Socket) -> Boolean = { false },
    ): ByteArray? {
        val hosts = linkedSetOf(primaryHost).apply {
            fallbackHosts.forEach { if (it != primaryHost) add(it) }
        }.toList()

        for (host in hosts) {
            val udp = resolveUdp(host, primaryPort, query, queryLen, protectUDP)
            if (udp != null) {
                if (isTruncated(udp)) {
                    val tcp = resolveTcp(host, primaryPort, query, queryLen, protectTCP)
                    if (tcp != null) {
                        tcpFallbackCount.incrementAndGet()
                        successCount.incrementAndGet()
                        return tcp
                    }
                }
                successCount.incrementAndGet()
                return udp
            }
            val tcp = resolveTcp(host, primaryPort, query, queryLen, protectTCP)
            if (tcp != null) {
                tcpFallbackCount.incrementAndGet()
                successCount.incrementAndGet()
                return tcp
            }
        }
        failureCount.incrementAndGet()
        return null
    }

    /** Backward-compatible entry used by older call sites. */
    fun resolve(
        query: ByteArray,
        queryLen: Int,
        protect: (DatagramSocket) -> Boolean,
    ): ByteArray? = resolve(query, queryLen, protectUDP = protect, protectTCP = { false })

    private fun resolveUdp(
        host: String,
        port: Int,
        query: ByteArray,
        queryLen: Int,
        protect: (DatagramSocket) -> Boolean,
    ): ByteArray? {
        repeat(udpAttemptsPerHost) {
            val socket = DatagramSocket()
            try {
                if (!protect(socket)) return null
                socket.soTimeout = udpTimeoutMs
                val upstream = InetSocketAddress(InetAddress.getByName(host), port)
                socket.send(DatagramPacket(query, queryLen, upstream))
                val buf = ByteArray(4096)
                val recv = DatagramPacket(buf, buf.size)
                socket.receive(recv)
                if (recv.length >= 12) {
                    return buf.copyOf(recv.length)
                }
            } catch (_: SocketTimeoutException) {
                // retry
            } catch (_: Exception) {
                return null
            } finally {
                socket.close()
            }
        }
        return null
    }

    private fun resolveTcp(
        host: String,
        port: Int,
        query: ByteArray,
        queryLen: Int,
        protect: (Socket) -> Boolean,
    ): ByteArray? {
        val socket = Socket()
        try {
            if (!protect(socket)) return null
            socket.soTimeout = tcpTimeoutMs
            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), tcpTimeoutMs)
            val out = DataOutputStream(socket.getOutputStream())
            out.writeShort(queryLen)
            out.write(query, 0, queryLen)
            out.flush()
            val input = DataInputStream(socket.getInputStream())
            val len = input.readUnsignedShort()
            if (len < 12 || len > 65535) return null
            val body = ByteArray(len)
            input.readFully(body)
            return body
        } catch (_: Exception) {
            return null
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun isTruncated(response: ByteArray): Boolean {
        if (response.size < 4) return false
        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        return (flags and 0x0200) != 0 // TC bit
    }
}

data class UpstreamStats(
    val success: Long,
    val failure: Long,
    val tcpFallbacks: Long,
    val primary: String,
)
