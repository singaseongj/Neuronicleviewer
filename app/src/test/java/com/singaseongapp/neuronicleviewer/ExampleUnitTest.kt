package com.singaseongapp.neuronicleviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun decodePacket_extractsChannelValuesAndStatusFlags() {
        val packet = intArrayOf(
            0,
            0b0011_1000,
            1,
            0,
            75,
            0x01,
            0x80,
            0x7F,
            0xFF
        )

        val frame = PacketParser.decodePacket(packet)

        assertEquals(384, frame.reading.ch1)
        assertEquals(-1, frame.reading.ch2)
        assertTrue(frame.status.ch1Connected)
        assertTrue(frame.status.ch2Connected)
        assertTrue(frame.status.refConnected)
        assertEquals(75, frame.status.batteryPercent)
    }

    @Test
    fun streamDecoder_aggregatesCyclicStatusAcrossPackets() {
        val bytes = byteArrayOf(
            0x55,
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x38, 0x00, 0x00, 0x14, 0x01, 0x00, 0x02, 0x00,
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x38, 0x01, 0x00, 0x50, 0x01, 0x00, 0x02, 0x00,
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x38, 0x02, 0x00, 0x01, 0x01, 0x00, 0x02, 0x00
        )

        val frames = PacketParser.StreamDecoder().consume(bytes, bytes.size)
        val last = frames.last()

        assertEquals(3, frames.size)
        assertTrue(last.status.bandWorn)
        assertFalse(last.status.lowBatteryWarning)
        assertEquals(80, last.status.batteryPercent)
        assertTrue(last.status.clipElectrodeOk)
        assertTrue(last.status.ch1Connected)
        assertTrue(last.status.ch2Connected)
        assertTrue(last.status.refConnected)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodePacket_requiresMinimumPacketLength() {
        PacketParser.decodePacket(intArrayOf(1, 2, 3))
    }

    @Test
    fun streamDecoder_returnsEmptyUntilPacketComplete() {
        val partial = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x38, 0x00)

        val readings = PacketParser.StreamDecoder().consume(partial, partial.size)

        assertTrue(readings.isEmpty())
    }
}
