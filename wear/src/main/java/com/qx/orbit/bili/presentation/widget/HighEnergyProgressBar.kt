package com.qx.orbit.bili.presentation.widget

import android.R.attr.seekBarStyle
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import kotlin.math.pow
import androidx.core.graphics.toColorInt

class HighEnergyProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var highEnergyData: FloatArray = floatArrayOf()
    private var stepSec: Int = 0
    private var showHighEnergy: Boolean = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#A8FB7299".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#33FB7299".toColorInt()
        style = Paint.Style.FILL
    }

    private val path = Path()

    fun setHighEnergyData(data: FloatArray, stepSec: Int) {
        this.highEnergyData = data
        this.stepSec = stepSec
        invalidate()
    }

    fun setShowHighEnergy(show: Boolean) {
        this.showHighEnergy = show
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showHighEnergy || highEnergyData.isEmpty() || stepSec <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val maxProgress = max
        if (maxProgress <= 0) return

        val stepPx = w * stepSec / maxProgress.toFloat()
        val barHeight = h * 0.6f
        val baseY = h * 0.2f

        path.reset()
        path.moveTo(0f, h)

        var x = 0f
        for (i in highEnergyData.indices) {
            val density = highEnergyData[i].coerceIn(0f, 1f).pow(0.7f)
            val y = h - density * barHeight - baseY
            if (i == 0) {
                path.lineTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            x += stepPx
            if (x > w) break
        }

        path.lineTo(x.coerceAtMost(w), h)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
