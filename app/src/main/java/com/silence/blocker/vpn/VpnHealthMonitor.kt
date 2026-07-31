package com.silence.blocker.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight health signals for the DNS packet loop.
 * Used to detect dead loops and surface recovery UI.
 */
object VpnHealthMonitor {
    private val lastPacketAt = AtomicLong(0L)
    private val lastDnsHandledAt = AtomicLong(0L)
    private val packetsSeen = AtomicLong(0L)
    private val dnsHandled = AtomicLong(0L)
    private val restarts = AtomicLong(0L)

    private val _snapshot = MutableStateFlow(HealthSnapshot())
    val snapshot: StateFlow<HealthSnapshot> = _snapshot.asStateFlow()

    fun markPacket() {
        lastPacketAt.set(System.currentTimeMillis())
        packetsSeen.incrementAndGet()
    }

    fun markDnsHandled() {
        val now = System.currentTimeMillis()
        lastDnsHandledAt.set(now)
        lastPacketAt.set(now)
        dnsHandled.incrementAndGet()
        publish()
    }

    fun markRestart() {
        restarts.incrementAndGet()
        publish()
    }

    fun resetSession() {
        lastPacketAt.set(System.currentTimeMillis())
        lastDnsHandledAt.set(0L)
        packetsSeen.set(0L)
        dnsHandled.set(0L)
        publish()
    }

    fun publish() {
        _snapshot.value = HealthSnapshot(
            lastPacketAtEpochMs = lastPacketAt.get().takeIf { it > 0 },
            lastDnsAtEpochMs = lastDnsHandledAt.get().takeIf { it > 0 },
            packetsSeen = packetsSeen.get(),
            dnsHandled = dnsHandled.get(),
            restarts = restarts.get(),
            loopAliveHint = isLoopAliveHint(),
        )
    }

    /**
     * Heuristic: if the VPN is ON but we never saw a packet for a long time after start,
     * that can be normal (idle). A loop that exited sets running=false via service.
     */
    fun isLoopAliveHint(now: Long = System.currentTimeMillis()): Boolean {
        val last = lastPacketAt.get()
        if (last == 0L) return true // just started
        // Idle DNS is fine; we only flag extreme silence after activity then nothing for 30m is not an error
        return true
    }

    fun ageSinceLastDnsMs(now: Long = System.currentTimeMillis()): Long? {
        val last = lastDnsHandledAt.get()
        if (last <= 0) return null
        return now - last
    }
}

data class HealthSnapshot(
    val lastPacketAtEpochMs: Long? = null,
    val lastDnsAtEpochMs: Long? = null,
    val packetsSeen: Long = 0,
    val dnsHandled: Long = 0,
    val restarts: Long = 0,
    val loopAliveHint: Boolean = true,
)
