package com.silence.blocker.vpn

import com.silence.blocker.dns.DnsServer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only TUN loop for IPv4 and IPv6.
 *
 * Only traffic to the fake DNS addresses (see [SilenceVpnService]) is routed into the TUN.
 * We parse UDP/53 queries, run them through [DnsServer], and write responses back.
 */
class PacketLoop(
    private val dnsServer: DnsServer,
    private val protectUDP: (DatagramSocket) -> Boolean,
    private val protectTCP: (Socket) -> Boolean,
    private val vpnInput: FileInputStream,
    private val vpnOutput: FileOutputStream,
) {
    private val running = AtomicBoolean(false)
    private class Job(val query: ByteArray, val reply: (ByteArray) -> ByteArray)
    private val queue = java.util.concurrent.ArrayBlockingQueue<Job>(QUEUE_CAPACITY)
    private val workers = mutableListOf<Thread>()
    private val writeLock = Any()

    val isRunning: Boolean get() = running.get()

    fun start() {
        running.set(true)
        VpnHealthMonitor.resetSession()
        startWorkers()
        val buffer = ByteArray(32767)
        while (running.get()) {
            val length = try {
                vpnInput.read(buffer)
            } catch (_: Exception) {
                break
            }
            if (length <= 0) continue
            VpnHealthMonitor.markPacket()
            handlePacket(buffer, length)
        }
        running.set(false)
        stopWorkers()
    }

    fun stop() {
        running.set(false)
        stopWorkers()
    }

    private fun startWorkers() {
        repeat(WORKERS) { n ->
            val t = Thread({
                while (running.get()) {
                    val job = try { queue.poll(1, java.util.concurrent.TimeUnit.SECONDS) } catch (_: InterruptedException) { break } ?: continue
                    val resp = try {
                        dnsServer.handleQuery(job.query, job.query.size, protectUDP, protectTCP)
                    } catch (_: Exception) { null } ?: continue
                    VpnHealthMonitor.markDnsHandled()
                    writeTun(job.reply(resp))
                }
            }, "silence-dns-$n")
            t.isDaemon = true
            synchronized(workers) { workers.add(t) }
            t.start()
        }
    }

    private fun stopWorkers() {
        synchronized(workers) { workers.forEach { it.interrupt() }; workers.clear() }
        queue.clear()
    }

    /** Hand a query to the worker pool; if the queue is full answer SERVFAIL immediately. */
    private fun enqueue(query: ByteArray, reply: (ByteArray) -> ByteArray) {
        if (!queue.offer(Job(query, reply))) {
            val q = com.silence.blocker.dns.DnsPacket.parseQuestion(query, query.size) ?: return
            writeTun(reply(com.silence.blocker.dns.DnsPacket.buildServFail(query, query.size, q)))
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int) {
        if (length < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
        when (version) {
            4 -> handleIpv4(packet, length)
            6 -> handleIpv6(packet, length)
        }
    }

    private fun handleIpv4(packet: ByteArray, length: Int) {
        if (length < 28) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return // UDP only

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val udpOffset = ihl
        val srcPort = readU16(packet, udpOffset)
        val dstPort = readU16(packet, udpOffset + 2)
        if (dstPort != 53) return

        val udpLen = readU16(packet, udpOffset + 4)
        val dnsOffset = udpOffset + 8
        val dnsLen = (udpLen - 8).coerceAtMost(length - dnsOffset)
        if (dnsLen < 12) return

        val dnsQuery = packet.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        enqueue(dnsQuery) { resp ->
            buildIpv4UdpPacket(srcIp = dstIp, dstIp = srcIp, srcPort = 53, dstPort = srcPort, payload = resp)
        }
    }

    private fun handleIpv6(packet: ByteArray, length: Int) {
        // IPv6 fixed header is 40 bytes; we only handle UDP with no extension headers for MVP.
        if (length < 40 + 8) return
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != 17) return // UDP only (no hop-by-hop etc.)

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)

        val udpOffset = 40
        val srcPort = readU16(packet, udpOffset)
        val dstPort = readU16(packet, udpOffset + 2)
        if (dstPort != 53) return

        val udpLen = readU16(packet, udpOffset + 4)
        val dnsOffset = udpOffset + 8
        val dnsLen = (udpLen - 8).coerceAtMost(length - dnsOffset)
        if (dnsLen < 12) return

        val dnsQuery = packet.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        enqueue(dnsQuery) { resp ->
            buildIpv6UdpPacket(srcIp = dstIp, dstIp = srcIp, srcPort = 53, dstPort = srcPort, payload = resp)
        }
    }

    private fun writeTun(bytes: ByteArray) {
        synchronized(writeLock) {
            try {
                vpnOutput.write(bytes)
                vpnOutput.flush()
            } catch (_: Exception) {
                // TUN closed
            }
        }
    }

    private fun buildIpv4UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + payload.size
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        buf.put(0x45.toByte())
        buf.put(0)
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte())
        buf.putShort(0)
        buf.put(srcIp)
        buf.put(dstIp)

        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort((udpHeaderLen + payload.size).toShort())
        buf.putShort(0) // UDP checksum optional for IPv4
        buf.put(payload)

        val bytes = buf.array()
        val ipChecksum = onesComplementChecksum(bytes, 0, ipHeaderLen, skipOffset = 10)
        bytes[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        bytes[11] = (ipChecksum and 0xFF).toByte()
        return bytes
    }

    private fun buildIpv6UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 40 + udpLen
        val bytes = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // Version=6, Traffic Class=0, Flow=0
        buf.putInt(0x60000000)
        buf.putShort(udpLen.toShort()) // payload length
        buf.put(17.toByte()) // next header = UDP
        buf.put(64.toByte()) // hop limit
        buf.put(srcIp)
        buf.put(dstIp)

        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort(udpLen.toShort())
        buf.putShort(0) // checksum placeholder
        buf.put(payload)

        val udpChecksum = ipv6UdpChecksum(srcIp, dstIp, bytes, 40, udpLen)
        bytes[40 + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
        bytes[40 + 7] = (udpChecksum and 0xFF).toByte()
        return bytes
    }

    private fun ipv6UdpChecksum(
        src: ByteArray,
        dst: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLen: Int,
    ): Int {
        var sum = 0
        // Pseudo-header: src(16) + dst(16) + udpLen(4) + zeros(3) + next(1=17)
        sum = addBytes(sum, src)
        sum = addBytes(sum, dst)
        sum += (udpLen ushr 16) and 0xFFFF
        sum += udpLen and 0xFFFF
        sum += 17
        // UDP header+payload with checksum field zeroed
        var i = 0
        while (i + 1 < udpLen) {
            if (i == 6) {
                // skip checksum field
                i += 2
                continue
            }
            val word = ((packet[udpOffset + i].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < udpLen) {
            sum += (packet[udpOffset + i].toInt() and 0xFF) shl 8
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    private fun addBytes(sumIn: Int, bytes: ByteArray): Int {
        var sum = sumIn
        var i = 0
        while (i + 1 < bytes.size) {
            sum += ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            i += 2
        }
        return sum
    }

    private fun onesComplementChecksum(
        packet: ByteArray,
        offset: Int,
        length: Int,
        skipOffset: Int = -1,
    ): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            if (skipOffset >= 0 && i == skipOffset) {
                i += 2
                continue
            }
            val word = ((packet[offset + i].toInt() and 0xFF) shl 8) or
                (packet[offset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    companion object {
        const val QUEUE_CAPACITY = 256
        const val WORKERS = 8
    }

    private fun readU16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
}
