package com.singaseongapp.neuronicleviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun decodePacket_extractsChannelValues() {
        val packet = intArrayOf(0, 0x01, 0x02, 0x03, 0x04, 0, 0, 0, 0, 0)

        val reading = PacketParser.decodePacket(packet)

        assertEquals(130, reading.ch1)
        assertEquals(388, reading.ch2)
    }

    @Test
    fun streamDecoder_ignoresNoiseAndReturnsCompletedPacket() {
        val bytes = byteArrayOf(
            0x01,
            0x02,
            0x7F.toByte(),
            0xFF.toByte(),
            0xFE.toByte(),
            0x00,
            0x01,
            0x02,
            0x03,
            0x04,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00
        )

        val readings = PacketParser.StreamDecoder().consume(bytes, bytes.size)

        assertEquals(1, readings.size)
        assertEquals(130, readings.first().ch1)
        assertEquals(388, readings.first().ch2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodePacket_requiresMinimumPacketLength() {
        PacketParser.decodePacket(intArrayOf(1, 2, 3))
    }

    @Test
    fun streamDecoder_returnsEmptyUntilPacketComplete() {
        val partial = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x01, 0x02)

        val readings = PacketParser.StreamDecoder().consume(partial, partial.size)

        assertTrue(readings.isEmpty())
    }
}
