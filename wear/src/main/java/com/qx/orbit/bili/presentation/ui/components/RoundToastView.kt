package com.qx.orbit.bili.presentation.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.min

class RoundToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val path = Path()
    private val arcRect = RectF()

    private var toastText: String = ""

    // 🚀 更具高级感的透明度与颜色：深灰色透明背景
    private val toastColor = 0xCC1A1A1A.toInt() 
    private val borderColor = 0x33FFFFFF // 极细白边，增加立体感
    private val textColor = 0xFFFFFFFF.toInt()

    private val textSizeVal = dp(13).toFloat() // 略微调小字体更精致

    private val bgThickness = dp(24).toFloat() // 略微收窄厚度

    private val borderWidth = dp(0.8f).toFloat()
    private val bottomMargin = dp(8).toFloat() // 更靠下

    private val minSweepAngle = 20f
    private val maxSweepAngle = 140f
    private val textPadding = dp(20).toFloat()

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = borderColor
        borderPaint.strokeWidth = bgThickness + (borderWidth * 2)
        borderPaint.strokeCap = Paint.Cap.ROUND

        bgPaint.style = Paint.Style.STROKE
        bgPaint.color = toastColor
        bgPaint.strokeWidth = bgThickness
        bgPaint.strokeCap = Paint.Cap.ROUND

        textPaint.color = textColor
        textPaint.textSize = textSizeVal
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.05f // 字间距增加精致感
    }

    fun setText(text: CharSequence) {
        toastText = text.toString()
        if (width > 0 && height > 0) {
            updatePath(width.toFloat(), height.toFloat())
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePath(w.toFloat(), h.toFloat())
    }

    private fun updatePath(w: Float, h: Float) {
        path.reset()
        if (toastText.isEmpty()) return

        val screenRadius = min(w, h) / 2f
        val totalThickness = bgThickness + (borderWidth * 2)
        
        // 🚀 核心优化：Arc 半径动态调整，使其弧度与屏幕边缘完全平行且留白自然
        val arcRadius = screenRadius - bottomMargin - (totalThickness / 2f)

        val cx = w / 2f
        val cy = h / 2f
        arcRect.set(
            cx - arcRadius,
            cy - arcRadius,
            cx + arcRadius,
            cy + arcRadius
        )

        val textWidth = textPaint.measureText(toastText)

        // πd -> 周长
        val perimeter = 2 * PI * arcRadius

        // 计算需要的长度百分比 -> 角度
        val targetArcLength = textWidth + textPadding
        var calculatedSweep = (targetArcLength / perimeter * 360).toFloat()

        calculatedSweep = calculatedSweep.coerceIn(minSweepAngle, maxSweepAngle)

        // 向下取整，使文字居于底部
        val sweepAngle = -calculatedSweep
        val startAngle = 90f - (sweepAngle / 2f)

        path.addArc(arcRect, startAngle, sweepAngle)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (toastText.isEmpty()) return
        
        // 🚀 绘制顺序：外圈描边 -> 内圈背景
        canvas.drawPath(path, borderPaint)
        canvas.drawPath(path, bgPaint)

        // 🚀 绘制文字：使用 FontMetrics 精确计算垂直偏移，确保在 bar 正中央
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val vOffset = (textHeight / 2f) - fontMetrics.descent
        canvas.drawTextOnPath(toastText, path, 0f, vOffset, textPaint)
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    private fun dp(value: Float): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}
