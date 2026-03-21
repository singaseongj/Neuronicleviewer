package com.singaseongapp.neuronicleviewer

object PacketParser {
    private const val SYNC_FIRST = 0xFF
    private const val SYNC_SECOND = 0xFE
    private const val PACKET_LENGTH = 9

    private const val PUD2_INDEX = 1
    private const val PACKET_COUNT_INDEX = 2
    private const val CYCLIC_DATA_INDEX = 4
    private const val CH1_MSB_INDEX = 5
    private const val CH1_LSB_INDEX = 6
    private const val CH2_MSB_INDEX = 7
    private const val CH2_LSB_INDEX = 8

    private const val ELECTRODE_REF_MASK = 1 shl 3
    private const val ELECTRODE_CH2_MASK = 1 shl 4
    private const val ELECTRODE_CH1_MASK = 1 shl 5

    private const val LOW_BATTERY_MASK = 1 shl 2
    private const val BAND_WORN_MASK = 1 shl 4
    private const val CLIP_OK_MASK = 1 shl 0

    data class ChannelReading(val ch1: Int, val ch2: Int)

    data class DeviceStatus(
        val ch1Connected: Boolean = false,
        val ch2Connected: Boolean = false,
        val refConnected: Boolean = false,
        val bandWorn: Boolean = false,
        val lowBatteryWarning: Boolean = false,
        val batteryPercent: Int? = null,
        val clipElectrodeOk: Boolean = false
    )

    private data class PacketData(
        val reading: ChannelReading,
        val ch1Connected: Boolean,
        val ch2Connected: Boolean,
        val refConnected: Boolean,
        val packetCount: Int,
        val cyclicData: Int
    )

    data class DeviceFrame(
        val reading: ChannelReading,
        val status: DeviceStatus
    )

    class StreamDecoder {
        private var lastByte = -1
        private val packet = IntArray(PACKET_LENGTH)
        private var packetIndex = 0
        private var isSynced = false
        private var currentStatus = DeviceStatus()

        fun consume(bytes: ByteArray, length: Int): List<DeviceFrame> {
            val frames = mutableListOf<DeviceFrame>()
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
                        val packetData = decodePacketData(packet)
                        currentStatus = mergeStatus(currentStatus, packetData)
                        frames += DeviceFrame(
                            reading = packetData.reading,
                            status = currentStatus
                        )
                        isSynced = false
                    }
                }
                lastByte = currentByte
            }
            return frames
        }
    }

    fun decodePacket(packet: IntArray): DeviceFrame {
        require(packet.size >= PACKET_LENGTH) { "Packet must contain at least $PACKET_LENGTH bytes." }
        val packetData = decodePacketData(packet)
        return DeviceFrame(reading = packetData.reading, status = mergeStatus(DeviceStatus(), packetData))
    }

    private fun decodePacketData(packet: IntArray): PacketData {
        val reading = ChannelReading(
            ch1 = decodeSigned15Bit(packet[CH1_MSB_INDEX], packet[CH1_LSB_INDEX]),
            ch2 = decodeSigned15Bit(packet[CH2_MSB_INDEX], packet[CH2_LSB_INDEX])
        )

        val pud2 = packet[PUD2_INDEX]
        return PacketData(
            reading = reading,
            ch1Connected = (pud2 and ELECTRODE_CH1_MASK) != 0,
            ch2Connected = (pud2 and ELECTRODE_CH2_MASK) != 0,
            refConnected = (pud2 and ELECTRODE_REF_MASK) != 0,
            packetCount = packet[PACKET_COUNT_INDEX],
            cyclicData = packet[CYCLIC_DATA_INDEX]
        )
    }

    private fun mergeStatus(previous: DeviceStatus, packetData: PacketData): DeviceStatus {
        var status = previous.copy(
            ch1Connected = packetData.ch1Connected,
            ch2Connected = packetData.ch2Connected,
            refConnected = packetData.refConnected
        )

        when (packetData.packetCount) {
            0 -> status = status.copy(
                bandWorn = (packetData.cyclicData and BAND_WORN_MASK) != 0,
                lowBatteryWarning = (packetData.cyclicData and LOW_BATTERY_MASK) != 0
            )
            1 -> status = status.copy(
                batteryPercent = packetData.cyclicData.coerceIn(0, 100)
            )
            2 -> status = status.copy(
                clipElectrodeOk = (packetData.cyclicData and CLIP_OK_MASK) != 0
            )
        }

        return status
    }

    private fun decodeSigned15Bit(highByte: Int, lowByte: Int): Int {
        val raw = ((highByte and 0x7F) shl 8) or (lowByte and 0xFF)
        return if (raw >= 0x4000) raw - 0x8000 else raw
    }
}
