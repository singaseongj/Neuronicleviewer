package com.singaseongapp.neuronicleviewer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalFilterPipelineTest {

    @Test
    fun bandpassAndNotchReduceOutOfBandNoiseWhileKeepingBrainwaveRange() {
        val sampleRateHz = SignalFilterPipeline.DEFAULT_SAMPLE_RATE_HZ
        val pipeline = SignalFilterPipeline(sampleRateHz = sampleRateHz)

        val lowFreqInput = sineWave(sampleRateHz, frequencyHz = 0.2)
        val brainwaveInput = sineWave(sampleRateHz, frequencyHz = 10.0)
        val highFreqInput = sineWave(sampleRateHz, frequencyHz = 80.0)
        val humInput = sineWave(sampleRateHz, frequencyHz = 60.0)

        val lowFreqOutput = measureAmplitude(lowFreqInput) { sample ->
            pipeline.process(PacketParser.ChannelReading(sample, 0)).ch1
        }
        pipeline.reset()

        val brainwaveOutput = measureAmplitude(brainwaveInput) { sample ->
            pipeline.process(PacketParser.ChannelReading(sample, 0)).ch1
        }
        pipeline.reset()

        val highFreqOutput = measureAmplitude(highFreqInput) { sample ->
            pipeline.process(PacketParser.ChannelReading(sample, 0)).ch1
        }
        pipeline.reset()

        val humOutput = measureAmplitude(humInput) { sample ->
            pipeline.process(PacketParser.ChannelReading(sample, 0)).ch1
        }

        assertTrue("10 Hz signal should remain stronger than 0.2 Hz drift", brainwaveOutput > lowFreqOutput * 4)
        assertTrue("10 Hz signal should remain stronger than 80 Hz noise", brainwaveOutput > highFreqOutput * 4)
        assertTrue("60 Hz hum should be strongly attenuated", humOutput < brainwaveOutput * 0.35)
    }

    @Test
    fun movingAverageSoftensSharpSpikes() {
        val pipeline = SignalFilterPipeline()
        repeat(20) {
            pipeline.process(PacketParser.ChannelReading(0, 0))
        }

        val spikeOutputs = buildList {
            add(pipeline.process(PacketParser.ChannelReading(1000, 0)).ch1)
            repeat(4) {
                add(pipeline.process(PacketParser.ChannelReading(0, 0)).ch1)
            }
        }

        assertTrue("Smoothing should spread the spike over several samples", spikeOutputs.drop(1).any { abs(it) > 0 })
        assertTrue("Smoothing should reduce the first displayed spike", abs(spikeOutputs.first()) < 1000)
    }

    private fun sineWave(
        sampleRateHz: Double,
        frequencyHz: Double,
        amplitude: Double = 1000.0,
        sampleCount: Int = 1500
    ): List<Int> {
        return List(sampleCount) { index ->
            val theta = 2.0 * PI * frequencyHz * index / sampleRateHz
            (sin(theta) * amplitude).toInt()
        }
    }

    private fun measureAmplitude(
        samples: List<Int>,
        process: (Int) -> Int
    ): Double {
        val steadyState = samples.map(process).drop(samples.size / 3)
        return steadyState.map { abs(it) }.average()
    }
}
