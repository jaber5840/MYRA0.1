package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE, LISTENING, SPEAKING, THINKING
    }

    private var currentState = State.IDLE

    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintCore = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintWave = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintThinking = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val paintParticle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG)

    private var scaleFactor = 1.0f
    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var thinkingAngle = 0f
    private var glowAlpha = 180

    var amplitude = 0.2f
        set(value) {
            field = value.coerceIn(0.1f, 1.0f)
            invalidate()
        }

    private val pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f, 1.0f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            scaleFactor = it.animatedValue as Float
            glowAlpha = (120 + (scaleFactor - 1.0f) * 660).toInt().coerceIn(100, 255)
            invalidate()
        }
    }

    private val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            waveOffset += 0.15f
            thinkingAngle = (thinkingAngle + 4f) % 360f
            invalidate()
        }
    }

    private val particles = List(12) { i ->
        Particle(
            angle = (i * 30).toFloat(),
            distanceFactor = 0.8f + (i % 3) * 0.15f,
            speed = 1.0f + (i % 4) * 0.5f,
            size = 4f + (i % 3) * 2f
        )
    }

    init {
        pulseAnimator.start()
        rotationAnimator.start()
    }

    fun setState(state: State) {
        if (currentState != state) {
            currentState = state
            when (state) {
                State.IDLE -> {
                    pulseAnimator.duration = 1800
                }
                State.LISTENING -> {
                    pulseAnimator.duration = 1000
                }
                State.SPEAKING -> {
                    pulseAnimator.duration = 800
                }
                State.THINKING -> {
                    pulseAnimator.duration = 1200
                }
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = Math.min(cx, cy) * 0.55f
        val radius = baseRadius * scaleFactor

        if (radius <= 0) return

        val (colorStart, colorEnd) = when (currentState) {
            State.IDLE -> Color.parseColor("#B71C1C") to Color.parseColor("#880E4F")
            State.LISTENING -> Color.parseColor("#FF1744") to Color.parseColor("#D500F9")
            State.SPEAKING -> Color.parseColor("#E040FB") to Color.parseColor("#FF1744")
            State.THINKING -> Color.parseColor("#40C4FF") to Color.parseColor("#00B0FF")
        }

        // 1. Radial Glow Layer
        paintGlow.shader = RadialGradient(
            cx, cy, radius * 1.6f,
            intArrayOf(adjustAlpha(colorStart, glowAlpha), Color.TRANSPARENT),
            floatArrayOf(0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.6f, paintGlow)

        // 2. Core Orb Sphere
        paintCore.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.2f,
            intArrayOf(colorStart, colorEnd, Color.parseColor("#050505")),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paintCore)

        // 3. Rotating Rings (3 dashed arcs)
        paintRing.color = colorStart
        paintRing.pathEffect = DashPathEffect(floatArrayOf(30f, 20f), 0f)
        for (i in 1..3) {
            val ringRadius = radius * (1.1f + i * 0.15f)
            canvas.save()
            val speedDir = if (i % 2 == 0) 1f else -1f
            canvas.rotate(rotationAngle * speedDir * (1 + i * 0.3f), cx, cy)
            canvas.drawCircle(cx, cy, ringRadius, paintRing)
            canvas.restore()
        }

        // 4. Wave Rings (sine wave around perimeter)
        if (currentState == State.LISTENING || currentState == State.SPEAKING) {
            paintWave.color = colorEnd
            val path = Path()
            val points = 72
            for (p in 0..points) {
                val angle = (p.toFloat() / points) * 2 * Math.PI
                val waveComp = sin(angle * 6 + waveOffset) * (8f + amplitude * 20f)
                val r = radius * 1.08f + waveComp
                val px = (cx + r * cos(angle)).toFloat()
                val py = (cy + r * sin(angle)).toFloat()
                if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            canvas.drawPath(path, paintWave)
        }

        // 5. Thinking Arc
        if (currentState == State.THINKING) {
            paintThinking.color = Color.parseColor("#40C4FF")
            val arcRadius = radius * 1.25f
            canvas.drawArc(
                cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius,
                thinkingAngle, 80f, false, paintThinking
            )
            canvas.drawArc(
                cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius,
                thinkingAngle + 180f, 80f, false, paintThinking
            )
        }

        // 6. Particles (orbiting dots)
        if (currentState == State.SPEAKING || currentState == State.LISTENING) {
            paintParticle.color = colorStart
            for (p in particles) {
                val pAngle = Math.toRadians((p.angle + rotationAngle * p.speed).toDouble())
                val pDist = radius * p.distanceFactor * (1f + amplitude * 0.2f)
                val px = (cx + pDist * cos(pAngle)).toFloat()
                val py = (cy + pDist * sin(pAngle)).toFloat()
                canvas.drawCircle(px, py, p.size, paintParticle)
            }
        }

        // 7. Inner Highlight
        paintHighlight.shader = RadialGradient(
            cx - radius * 0.35f, cy - radius * 0.35f, radius * 0.4f,
            intArrayOf(Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - radius * 0.2f, cy - radius * 0.2f, radius * 0.35f, paintHighlight)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
    }

    private data class Particle(
        val angle: Float,
        val distanceFactor: Float,
        val speed: Float,
        val size: Float
    )
}
