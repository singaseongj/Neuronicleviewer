package com.singaseongapp.neuronicleviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class EegGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val maxSamples = 500
    private val ch1Samples = ArrayDeque<Int>()
    private val ch2Samples = ArrayDeque<Int>()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val ch1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val ch2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29B6F6")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    fun addReading(reading: FilteredChannelReading) {
        append(ch1Samples, reading.ch1)
        append(ch2Samples, reading.ch2)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#101820"))
        drawGrid(canvas)
        drawWave(canvas, ch1Samples, ch1Paint)
        drawWave(canvas, ch2Samples, ch2Paint)
    }

    private fun drawGrid(canvas: Canvas) {
        val midY = height / 2f
        canvas.drawLine(0f, midY, width.toFloat(), midY, axisPaint)
        repeat(4) { index ->
            val y = height * (index + 1) / 5f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        repeat(5) { index ->
            val x = width * index / 4f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
    }

    private fun drawWave(canvas: Canvas, samples: ArrayDeque<Int>, paint: Paint) {
        if (samples.size < 2) return

        val maxAmplitude = max(512f, samples.maxOf { abs(it) }.toFloat())
        val xStep = width.toFloat() / (maxSamples - 1).coerceAtLeast(1)
        val midY = height / 2f
        val scaleY = (height * 0.42f) / maxAmplitude
        val path = Path()

        samples.forEachIndexed { index, value ->
            val x = index * xStep
            val y = midY - (value * scaleY)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, paint)
    }

    private fun append(samples: ArrayDeque<Int>, value: Int) {
        while (samples.size >= maxSamples) {
            samples.removeFirst()
        }
        samples.addLast(value)
    }
}
