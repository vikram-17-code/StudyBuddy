package com.example.studybuddy.ui.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.studybuddy.R
import androidx.core.content.ContextCompat

class CircularPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private val strokeWidth = 30f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        this.strokeWidth = this@CircularPieChartView.strokeWidth
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.green_primary)
        style = Paint.Style.STROKE
        this.strokeWidth = this@CircularPieChartView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black)
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rectF = RectF()

    private var animator: android.animation.ValueAnimator? = null

    fun setProgress(value: Float, animate: Boolean = true) {
        val targetProgress = value.coerceIn(0f, 100f)
        if (animate) {
            animator?.cancel()
            animator = android.animation.ValueAnimator.ofFloat(progress, targetProgress).apply {
                duration = 1000
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            progress = targetProgress
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val radius = (minOf(width, height) - strokeWidth) / 2

        rectF.set(
            width / 2 - radius,
            height / 2 - radius,
            width / 2 + radius,
            height / 2 + radius
        )

        // Draw background circle
        canvas.drawCircle(width / 2, height / 2, radius, backgroundPaint)

        // Draw progress arc
        val sweepAngle = (progress / 100f) * 360f
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // Draw percentage text
        val text = "${progress.toInt()}%"
        val textHeight = textPaint.descent() + textPaint.ascent()
        canvas.drawText(text, width / 2, (height - textHeight) / 2, textPaint)
    }
}