package com.example.littleclicker.autoclick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class SntpResult(
    val serverHost: String,
    val offsetMillis: Long,
    val delayMillis: Long,
)

internal object SntpClient {
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_TIME_OFFSET = 2_208_988_800L
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3

    suspend fun query(serverHost: String, timeoutMillis: Int = 1_500): SntpResult =
        withContext(Dispatchers.IO) {
            val address = InetAddress.getByName(serverHost)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis

                val buffer = ByteArray(NTP_PACKET_SIZE)
                buffer[0] = ((NTP_VERSION shl 3) or NTP_MODE_CLIENT).toByte()
                val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

                val requestTime = System.currentTimeMillis()
                socket.send(request)

                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val responseTime = System.currentTimeMillis()

                val serverReceiveTime = readTimestamp(buffer, 32)
                val serverTransmitTime = readTimestamp(buffer, 40)
                val offset = ((serverReceiveTime - requestTime) + (serverTransmitTime - responseTime)) / 2L
                val delay = (responseTime - requestTime) - (serverTransmitTime - serverReceiveTime)

                SntpResult(
                    serverHost = serverHost,
                    offsetMillis = offset,
                    delayMillis = delay.coerceAtLeast(0L)
                )
            }
        }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        val seconds = read32(buffer, offset)
        val fraction = read32(buffer, offset + 4)
        val unixSeconds = seconds - NTP_TIME_OFFSET
        val milliseconds = (fraction * 1_000L) / 0x1_0000_0000L
        return unixSeconds * 1_000L + milliseconds
    }

    private fun read32(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xFFL) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFFL) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFFL) shl 8) or
            (buffer[offset + 3].toLong() and 0xFFL)
    }
}
