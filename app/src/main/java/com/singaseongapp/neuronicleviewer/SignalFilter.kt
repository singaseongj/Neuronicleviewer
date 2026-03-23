package com.singaseongapp.neuronicleviewer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class FilteredChannelReading(
    val ch1: Int,
    val ch2: Int
)

class SignalFilterPipeline(
    sampleRateHz: Double = DEFAULT_SAMPLE_RATE_HZ,
    notchFrequencyHz: Double = DEFAULT_NOTCH_FREQUENCY_HZ,
    smoothingWindowSize: Int = DEFAULT_SMOOTHING_WINDOW_SIZE
) {
    private val ch1Filter = ChannelFilter(sampleRateHz, notchFrequencyHz, smoothingWindowSize)
    private val ch2Filter = ChannelFilter(sampleRateHz, notchFrequencyHz, smoothingWindowSize)

    fun process(reading: PacketParser.ChannelReading): FilteredChannelReading {
        return FilteredChannelReading(
            ch1 = ch1Filter.process(reading.ch1.toDouble()).roundToInt(),
            ch2 = ch2Filter.process(reading.ch2.toDouble()).roundToInt()
        )
    }

    fun reset() {
        ch1Filter.reset()
        ch2Filter.reset()
    }

    private class ChannelFilter(
        sampleRateHz: Double,
        notchFrequencyHz: Double,
        smoothingWindowSize: Int
    ) {
        private val highPass = BiquadFilter.highPass(sampleRateHz, 1.0, 0.707)
        private val lowPass = BiquadFilter.lowPass(sampleRateHz, 40.0, 0.707)
        private val notch = BiquadFilter.notch(sampleRateHz, notchFrequencyHz, 8.0)
        private val smoother = MovingAverageFilter(smoothingWindowSize)

        fun process(input: Double): Double {
            val bandPassed = lowPass.process(highPass.process(input))
            val humRemoved = notch.process(bandPassed)
            return smoother.process(humRemoved)
        }

        fun reset() {
            highPass.reset()
            lowPass.reset()
            notch.reset()
            smoother.reset()
        }
    }

    private class MovingAverageFilter(private val windowSize: Int) {
        private val window = ArrayDeque<Double>()
        private var sum = 0.0

        fun process(input: Double): Double {
            window.addLast(input)
            sum += input
            while (window.size > windowSize) {
                sum -= window.removeFirst()
            }
            return sum / window.size.coerceAtLeast(1)
        }

        fun reset() {
            window.clear()
            sum = 0.0
        }
    }

    private class BiquadFilter(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Double): Double {
            val output = (b0 * input) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2)
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }

        companion object {
            fun highPass(sampleRateHz: Double, cutoffHz: Double, q: Double): BiquadFilter {
                val omega = 2.0 * PI * cutoffHz / sampleRateHz
                val alpha = sin(omega) / (2.0 * q)
                val cosOmega = cos(omega)
                return normalized(
                    rawB0 = (1.0 + cosOmega) / 2.0,
                    rawB1 = -(1.0 + cosOmega),
                    rawB2 = (1.0 + cosOmega) / 2.0,
                    rawA0 = 1.0 + alpha,
                    rawA1 = -2.0 * cosOmega,
                    rawA2 = 1.0 - alpha
                )
            }

            fun lowPass(sampleRateHz: Double, cutoffHz: Double, q: Double): BiquadFilter {
                val omega = 2.0 * PI * cutoffHz / sampleRateHz
                val alpha = sin(omega) / (2.0 * q)
                val cosOmega = cos(omega)
                return normalized(
                    rawB0 = (1.0 - cosOmega) / 2.0,
                    rawB1 = 1.0 - cosOmega,
                    rawB2 = (1.0 - cosOmega) / 2.0,
                    rawA0 = 1.0 + alpha,
                    rawA1 = -2.0 * cosOmega,
                    rawA2 = 1.0 - alpha
                )
            }

            fun notch(sampleRateHz: Double, centerHz: Double, q: Double): BiquadFilter {
                val omega = 2.0 * PI * centerHz / sampleRateHz
                val alpha = sin(omega) / (2.0 * q)
                val cosOmega = cos(omega)
                return normalized(
                    rawB0 = 1.0,
                    rawB1 = -2.0 * cosOmega,
                    rawB2 = 1.0,
                    rawA0 = 1.0 + alpha,
                    rawA1 = -2.0 * cosOmega,
                    rawA2 = 1.0 - alpha
                )
            }

            private fun normalized(
                rawB0: Double,
                rawB1: Double,
                rawB2: Double,
                rawA0: Double,
                rawA1: Double,
                rawA2: Double
            ): BiquadFilter {
                return BiquadFilter(
                    b0 = rawB0 / rawA0,
                    b1 = rawB1 / rawA0,
                    b2 = rawB2 / rawA0,
                    a1 = rawA1 / rawA0,
                    a2 = rawA2 / rawA0
                )
            }
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 250.0
        const val DEFAULT_NOTCH_FREQUENCY_HZ = 60.0
        const val DEFAULT_SMOOTHING_WINDOW_SIZE = 5
    }
}
