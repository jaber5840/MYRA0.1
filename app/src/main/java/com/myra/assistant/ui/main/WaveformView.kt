package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 0.1f }
    private val targetHeights = FloatArray(barCount) { 0.1f }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var currentRms = 0.1f
    private var isAnimating = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            updateHeights()
            invalidate()
        }
    }

    init {
        startAnimation()
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animator.start()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        animator.cancel()
        for (i in 0 until barCount) {
            targetHeights[i] = 0.05f
        }
        invalidate()
    }

    fun setAmplitude(rms: Float) {
        currentRms = rms.coerceIn(0.05f, 1.0f)
    }

    private fun updateHeights() {
        val time = System.currentTimeMillis() / 150.0
        for (i in 0 until barCount) {
            val wave = (sin(time + i * 0.4) + 1.0) / 2.0
            val noise = Random.nextFloat() * 0.2f
            targetHeights[i] = (currentRms * wave.toFloat() + noise).coerceIn(0.08f, 1.0f)
            barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.35f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = width.toFloat()
        val totalHeight = height.toFloat()
        val centerY = totalHeight / 2f

        val spacing = 6f
        val barWidth = ((totalWidth - (barCount - 1) * spacing) / barCount).coerceAtLeast(2f)

        for (i in 0 until barCount) {
            val barH = (barHeights[i] * totalHeight * 0.85f).coerceAtLeast(4f)
            val left = i * (barWidth + spacing)
            val top = centerY - barH / 2f
            val right = left + barWidth
            val bottom = centerY + barH / 2f

            val alpha = (150 + (barHeights[i] * 105)).toInt().coerceIn(150, 255)
            paint.color = Color.parseColor("#FF1744")
            paint.alpha = alpha

            canvas.drawRoundRect(left, top, right, bottom, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
