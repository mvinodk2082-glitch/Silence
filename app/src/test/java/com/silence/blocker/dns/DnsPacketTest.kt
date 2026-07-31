package com.silence.blocker.dns

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer

class DnsPacketTest {
    @Test
    fun parseAndSinkholeARecord() {
        val query = buildQuery("ads.example.com", DnsPacket.TYPE_A, id = 0x1234)
        val question = DnsPacket.parseQuestion(query)
        assertThat(question).isNotNull()
        assertThat(question!!.name).isEqualTo("ads.example.com")
        assertThat(question.type).isEqualTo(DnsPacket.TYPE_A)

        val response = DnsPacket.buildSinkholeResponse(query, query.size, question)
        val parsedBack = DnsPacket.parseQuestion(response)
        // response has QR bit set so parseQuestion returns null by design
        assertThat(parsedBack).isNull()
        assertThat(response.size).isGreaterThan(query.size)
        // last 4 bytes of A answer should be 0.0.0.0
        assertThat(response.takeLast(4)).isEqualTo(listOf(0.toByte(), 0, 0, 0))
    }

    @Test
    fun servFailSetsRcode() {
        val query = buildQuery("example.com", DnsPacket.TYPE_A, id = 0x42)
        val question = DnsPacket.parseQuestion(query)!!
        val response = DnsPacket.buildServFail(query, query.size, question)
        val flags = ((response[2].toInt() and 0xFF) shl 8) or (response[3].toInt() and 0xFF)
        assertThat(flags and 0xF).isEqualTo(DnsPacket.RCODE_SERVFAIL)
        assertThat(flags and 0x8000).isNotEqualTo(0) // QR
    }

    private fun buildQuery(name: String, type: Int, id: Int): ByteArray {
        val buf = ByteBuffer.allocate(256)
        buf.putShort(id.toShort())
        buf.putShort(0x0100.toShort()) // RD
        buf.putShort(1) // QD
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)
        for (label in name.split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            buf.put(b.size.toByte())
            buf.put(b)
        }
        buf.put(0)
        buf.putShort(type.toShort())
        buf.putShort(1) // IN
        return buf.array().copyOf(buf.position())
    }
}
