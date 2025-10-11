package com.kin.athena.service.vpn.network.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketUtil {
    var packetId: Int = 0
        get() = field++
        private set

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || offset >= buffer.size || length < 1 || length > 4) {
            throw IllegalArgumentException("Invalid offset ($offset) or length ($length) for buffer size ${buffer.size}")
        }
        var value = 0
        val end = (offset + length).coerceAtMost(buffer.size)
        for (i in offset until end) {
            value = value shl 8 or (buffer[i].toInt() and 0xFF)
        }
        return value
    }

    private fun getNetworkInt(buffer: ByteArray, start: Int, length: Int): Int {
        return read(buffer, start, length)
    }

    fun calculateChecksum(data: ByteArray, offset: Int, length: Int): ByteArray {
        var sum = 0L
        var i = offset
        while (i < length - 1) {
            sum += getNetworkInt(data, i, 2).toLong() and 0xFFFFL
            i += 2
        }
        if (i < length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFFL) + (sum shr 16)
        }
        val checksum = (sum.inv() and 0xFFFF).toInt()
        return byteArrayOf((checksum shr 8).toByte(), checksum.toByte())
    }

    fun calculateUDPHeaderChecksum(
        data: ByteArray?,
        offset: Int,
        udpLength: Int,
        destIp: Int,
        sourceIp: Int
    ): ByteArray {
        val bufferSize = if (udpLength % 2 == 0) udpLength + 12 else udpLength + 13
        val buffer = ByteBuffer.allocate(bufferSize).apply {
            order(ByteOrder.BIG_ENDIAN)
            putInt(sourceIp)
            putInt(destIp)
            put(0.toByte())
            put(17.toByte()) // UDP protocol number
            putShort(udpLength.toShort())
            data?.let { put(it, offset, udpLength) }
            if (udpLength % 2 != 0) put(0.toByte())
        }
        return calculateChecksum(buffer.array(), 0, bufferSize)
    }
}