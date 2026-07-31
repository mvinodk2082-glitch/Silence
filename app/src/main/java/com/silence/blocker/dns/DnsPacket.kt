package com.silence.blocker.dns

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal DNS message helpers for A/AAAA query inspection and sinkhole answers.
 * Not a full DNS implementation — enough for VPN DNS filtering MVP.
 */
object DnsPacket {
    data class Question(
        val name: String,
        val type: Int,
        val clazz: Int,
        val headerId: Int,
    )

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val CLASS_IN = 1
    const val RCODE_NXDOMAIN = 3
    const val RCODE_SERVFAIL = 2

    fun parseQuestion(packet: ByteArray, length: Int = packet.size): Question? {
        if (length < 12) return null
        val buf = ByteBuffer.wrap(packet, 0, length).order(ByteOrder.BIG_ENDIAN)
        val id = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        val qdCount = buf.short.toInt() and 0xFFFF
        buf.short // ANCOUNT
        buf.short // NSCOUNT
        buf.short // ARCOUNT
        if (qdCount < 1) return null
        // Only standard queries (QR=0)
        if ((flags and 0x8000) != 0) return null

        val name = readName(packet, buf) ?: return null
        if (buf.remaining() < 4) return null
        val type = buf.short.toInt() and 0xFFFF
        val clazz = buf.short.toInt() and 0xFFFF
        return Question(name = name, type = type, clazz = clazz, headerId = id)
    }

    fun buildSinkholeResponse(query: ByteArray, queryLen: Int, question: Question): ByteArray {
        if (question.type != TYPE_A && question.type != TYPE_AAAA) {
            return buildNxDomain(query, queryLen, question)
        }
        val out = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        out.putShort(question.headerId.toShort())
        val rd = if (queryLen >= 4) {
            ((query[2].toInt() and 0xFF) shl 8) or (query[3].toInt() and 0xFF) and 0x0100
        } else {
            0x0100
        }
        // QR | AA | RD | RA
        val flags = 0x8400 or rd or 0x0080
        out.putShort(flags.toShort())
        out.putShort(1) // QDCOUNT
        out.putShort(1) // ANCOUNT
        out.putShort(0)
        out.putShort(0)

        writeName(out, question.name)
        out.putShort(question.type.toShort())
        out.putShort(question.clazz.toShort())

        // Answer name as pointer to byte 12 (start of QNAME)
        out.putShort(0xC00C.toShort())
        out.putShort(question.type.toShort())
        out.putShort(question.clazz.toShort())
        out.putInt(60)
        when (question.type) {
            TYPE_A -> {
                out.putShort(4)
                out.putInt(0)
            }
            TYPE_AAAA -> {
                out.putShort(16)
                repeat(16) { out.put(0) }
            }
        }
        return out.array().copyOf(out.position())
    }

    fun buildNxDomain(query: ByteArray, queryLen: Int, question: Question): ByteArray {
        return buildErrorResponse(query, queryLen, question, RCODE_NXDOMAIN)
    }

    /** Upstream failed — tell the client so it can retry rather than hanging forever. */
    fun buildServFail(query: ByteArray, queryLen: Int, question: Question): ByteArray {
        return buildErrorResponse(query, queryLen, question, RCODE_SERVFAIL)
    }

    private fun buildErrorResponse(
        query: ByteArray,
        queryLen: Int,
        question: Question,
        rcode: Int,
    ): ByteArray {
        val out = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        out.putShort(question.headerId.toShort())
        val rd = if (queryLen >= 4) {
            ((query[2].toInt() and 0xFF) shl 8) or (query[3].toInt() and 0xFF) and 0x0100
        } else {
            0x0100
        }
        val flags = 0x8400 or rd or 0x0080 or (rcode and 0xF)
        out.putShort(flags.toShort())
        out.putShort(1)
        out.putShort(0)
        out.putShort(0)
        out.putShort(0)
        writeName(out, question.name)
        out.putShort(question.type.toShort())
        out.putShort(question.clazz.toShort())
        return out.array().copyOf(out.position())
    }

    /**
     * Reads a DNS name starting at [buf] position, supporting compression pointers.
     * Advances [buf] past the name as it appears in the message (not into the pointer target).
     */
    private fun readName(packet: ByteArray, buf: ByteBuffer): String? {
        val labels = ArrayList<String>()
        var jumped = false
        var pos = buf.position()
        var guard = 0

        while (guard++ < 128) {
            if (pos >= packet.size) return null
            val len = packet[pos].toInt() and 0xFF
            when {
                len == 0 -> {
                    if (!jumped) buf.position(pos + 1)
                    break
                }
                (len and 0xC0) == 0xC0 -> {
                    if (pos + 1 >= packet.size) return null
                    val pointer = ((len and 0x3F) shl 8) or (packet[pos + 1].toInt() and 0xFF)
                    if (!jumped) {
                        buf.position(pos + 2)
                        jumped = true
                    }
                    pos = pointer
                }
                else -> {
                    pos++
                    if (pos + len > packet.size) return null
                    labels.add(String(packet, pos, len, Charsets.US_ASCII))
                    pos += len
                    if (!jumped) {
                        // keep buf in sync when reading sequentially
                        buf.position(pos)
                    }
                }
            }
        }
        return labels.joinToString(".")
    }

    private fun writeName(buf: ByteBuffer, name: String) {
        if (name.isEmpty()) {
            buf.put(0)
            return
        }
        for (label in name.split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            require(bytes.size in 1..63) { "invalid label" }
            buf.put(bytes.size.toByte())
            buf.put(bytes)
        }
        buf.put(0)
    }
}
