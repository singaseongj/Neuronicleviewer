package com.singaseongapp.neuronicleviewer

object PacketParser {
    private const val SYNC_FIRST = 0xFF
    private const val SYNC_SECOND = 0xFE
    private const val PACKET_LENGTH = 10
    private const val CH1_MSB_INDEX = 1
    private const val CH1_LSB_INDEX = 2
    private const val CH2_MSB_INDEX = 3
    private const val CH2_LSB_INDEX = 4

    data class ChannelReading(val ch1: Int, val ch2: Int)

    class StreamDecoder {
        private var lastByte = -1
        private val packet = IntArray(PACKET_LENGTH)
        private var packetIndex = 0
        private var isSynced = false

        fun consume(bytes: ByteArray, length: Int): List<ChannelReading> {
            val readings = mutableListOf<ChannelReading>()
            repeat(length) { index ->
                val currentByte = bytes[index].toInt() and 0xFF
                if (lastByte == SYNC_FIRST && currentByte == SYNC_SECOND) {
                    isSynced = true
                    packetIndex = 0
                } else if (isSynced) {
                    if (packetIndex < PACKET_LENGTH) {
                        packet[packetIndex++] = currentByte
                    }
                    if (packetIndex == PACKET_LENGTH) {
                        readings += decodePacket(packet)
                        isSynced = false
                    }
                }
                lastByte = currentByte
            }
            return readings
        }
    }

    fun decodePacket(packet: IntArray): ChannelReading {
        require(packet.size >= PACKET_LENGTH) { "Packet must contain at least $PACKET_LENGTH bytes." }
        val ch1 = ((packet[CH1_MSB_INDEX] and 0x7F) shl 7) or (packet[CH1_LSB_INDEX] and 0x7F)
        val ch2 = ((packet[CH2_MSB_INDEX] and 0x7F) shl 7) or (packet[CH2_LSB_INDEX] and 0x7F)
        return ChannelReading(ch1 = ch1, ch2 = ch2)
    }
}
