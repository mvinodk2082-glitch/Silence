package com.silence.blocker.vpn

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VpnHealthMonitorTest {
    @Test
    fun tracksDnsAndRestarts() {
        VpnHealthMonitor.resetSession()
        VpnHealthMonitor.markDnsHandled()
        VpnHealthMonitor.markDnsHandled()
        VpnHealthMonitor.markRestart()
        val snap = VpnHealthMonitor.snapshot.value
        assertThat(snap.dnsHandled).isAtLeast(2)
        assertThat(snap.restarts).isAtLeast(1)
        assertThat(snap.lastDnsAtEpochMs).isNotNull()
    }
}
