package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
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
    private val paintGlassBody = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintBlob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val paintFresnel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val paintGyro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val paintHudArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val paintTick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val paintRipple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG)

    private val spherePath = Path()
    private val ovalRect = RectF()
    private val sweepMatrix = Matrix()

    private var phase = 0f
    private var breathe = 0f
    private var rippleProgress = 0f

    var amplitude = 0.15f
        set(value) {
            val clamped = value.coerceIn(0.05f, 1.0f)
            field += (clamped - field) * 0.35f
            invalidate()
        }

    private val spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val breatheAnimator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            breathe = it.animatedValue as Float
        }
    }

    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rippleProgress = it.animatedValue as Float
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        spinAnimator.start()
        breatheAnimator.start()
        rippleAnimator.start()
    }

    fun setState(state: State) {
        if (currentState != state) {
            currentState = state
            spinAnimator.duration = when (state) {
                State.IDLE -> 11000L
                State.LISTENING -> 6500L
                State.THINKING -> 4200L
                State.SPEAKING -> 5000L
            }
            rippleAnimator.duration = when (state) {
                State.SPEAKING -> 1100L
                State.LISTENING -> 1400L
                State.THINKING -> 1600L
                State.IDLE -> 2600L
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val minDim = min(w, h)
        if (minDim <= 0f) return

        val isVoiceActive = currentState == State.SPEAKING || currentState == State.LISTENING
        val voiceBoost = if (isVoiceActive) amplitude else 0.08f
        val pulse = 1f + sin(breathe) * 0.025f + voiceBoost * 0.055f

        val sphereR = minDim * 0.285f * pulse
        val outerHudR = minDim * 0.41f
        val midHudR = minDim * 0.355f

        // Harmonious palette per state (primary glow, secondary aurora, core highlight)
        val (c1, c2, c3) = when (currentState) {
            State.IDLE -> Triple(
                Color.parseColor("#FF1744"), // Crimson
                Color.parseColor("#FF4081"), // Luminous Rose
                Color.parseColor("#FF80AB")  // Soft Pearl Rose
            )
            State.LISTENING -> Triple(
                Color.parseColor("#FF1744"), // Electric Crimson
                Color.parseColor("#D500F9"), // Neon Magenta
                Color.parseColor("#40C4FF")  // Subtle Cyan Spark
            )
            State.THINKING -> Triple(
                Color.parseColor("#00E5FF"), // JARVIS Hologram Cyan
                Color.parseColor("#2979FF"), // Sapphire Blue
                Color.parseColor("#B388FF")  // Neural Violet
            )
            State.SPEAKING -> Triple(
                Color.parseColor("#FF1744"), // Crimson
                Color.parseColor("#E040FB"), // Siri Violet
                Color.parseColor("#00E5FF")  // Siri Cyan Shimmer
            )
        }

        // 1. Soft Seamless Outer Ambient Glow
        val haloR = minDim * 0.49f
        val haloAlpha = (95 + voiceBoost * 95).toInt().coerceIn(75, 195)
        paintGlow.shader = RadialGradient(
            cx, cy, haloR,
            intArrayOf(
                adjustAlpha(c1, haloAlpha),
                adjustAlpha(c2, (haloAlpha * 0.45f).toInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.25f, 0.62f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloR, paintGlow)

        // 2. Expanding Sound-Wave Ripple (subtle when idle, vivid when listening/speaking)
        val rippleR = sphereR * (1.06f + rippleProgress * 0.46f)
        val rippleFade = ((1f - rippleProgress) * (if (isVoiceActive) 135f * (0.4f + voiceBoost) else 45f))
            .toInt().coerceIn(0, 180)
        paintRipple.strokeWidth = maxOf(1.2f, minDim * 0.005f)
        paintRipple.color = adjustAlpha(c2, rippleFade)
        canvas.drawCircle(cx, cy, rippleR, paintRipple)

        // 3. Delicate Calibrated JARVIS Outer Ring & Hairline Ticks
        paintTick.strokeWidth = maxOf(0.9f, minDim * 0.003f)
        paintTick.color = adjustAlpha(c3, if (currentState == State.IDLE) 45 else 85)
        canvas.drawCircle(cx, cy, outerHudR, paintTick)

        val tickCount = 48
        for (i in 0 until tickCount) {
            val isMajor = i % 4 == 0
            if (!isMajor && minDim < 280f) continue // Keep small floating overlay crisp
            val deg = i * (360f / tickCount) + phase * 0.25f
            val rad = Math.toRadians(deg.toDouble())
            val rIn = if (isMajor) outerHudR - minDim * 0.022f else outerHudR - minDim * 0.011f
            paintTick.color = adjustAlpha(
                if (isMajor) c3 else c1,
                if (isMajor) (if (currentState == State.IDLE) 95 else 175) else 55
            )
            canvas.drawLine(
                (cx + rIn * cos(rad)).toFloat(),
                (cy + rIn * sin(rad)).toFloat(),
                (cx + outerHudR * cos(rad)).toFloat(),
                (cy + outerHudR * sin(rad)).toFloat(),
                paintTick
            )
        }

        // 4. Signature JARVIS Gradient-Tail Scanner Arcs
        drawGradientTailArc(
            canvas, cx, cy, midHudR,
            rotationDeg = phase * 1.4f,
            color = c1,
            strokeW = maxOf(1.8f, minDim * 0.0085f),
            alpha = if (currentState == State.IDLE) 150 else 235
        )
        drawGradientTailArc(
            canvas, cx, cy, midHudR * 0.91f,
            rotationDeg = -phase * 1.9f + 135f,
            color = c3,
            strokeW = maxOf(1.4f, minDim * 0.0065f),
            alpha = if (currentState == State.IDLE) 125 else 215
        )

        // 5. Volumetric Dark Obsidian Glass Sphere + Internal Siri Aurora Plasma
        paintGlassBody.shader = RadialGradient(
            cx, cy, sphereR,
            intArrayOf(
                Color.parseColor("#1A0A16"),
                Color.parseColor("#0D0712"),
                Color.parseColor("#05040A")
            ),
            floatArrayOf(0.0f, 0.72f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereR, paintGlassBody)

        // Clip inside glass sphere for fluid liquid-light aurora blobs
        spherePath.reset()
        spherePath.addCircle(cx, cy, sphereR * 0.985f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(spherePath)

        val angle1 = Math.toRadians((phase * 1.5f).toDouble())
        val angle2 = Math.toRadians((-phase * 1.2f + 120f).toDouble())
        val angle3 = Math.toRadians((phase * 0.9f + 240f).toDouble())

        val orbitDist = sphereR * (0.28f + voiceBoost * 0.10f)
        drawAuroraBlob(
            canvas,
            cx + (orbitDist * cos(angle1)).toFloat(),
            cy + (orbitDist * sin(angle1)).toFloat(),
            sphereR * (0.95f + voiceBoost * 0.22f),
            c1,
            (185 + voiceBoost * 65).toInt().coerceIn(160, 245)
        )
        drawAuroraBlob(
            canvas,
            cx + (orbitDist * cos(angle2)).toFloat(),
            cy + (orbitDist * sin(angle2)).toFloat(),
            sphereR * (0.88f + voiceBoost * 0.20f),
            c2,
            (175 + voiceBoost * 65).toInt().coerceIn(150, 240)
        )
        drawAuroraBlob(
            canvas,
            cx + (orbitDist * 0.85f * cos(angle3)).toFloat(),
            cy + (orbitDist * 0.85f * sin(angle3)).toFloat(),
            sphereR * (0.78f + voiceBoost * 0.18f),
            c3,
            (145 + voiceBoost * 75).toInt().coerceIn(120, 225)
        )

        // Delicate JARVIS Atomic / Gyro Core Iris inside the translucent orb
        paintGyro.strokeWidth = maxOf(1.0f, minDim * 0.0042f)
        val rx = sphereR * (0.72f + voiceBoost * 0.08f)
        val ry = sphereR * (0.28f + voiceBoost * 0.14f)
        ovalRect.set(cx - rx, cy - ry, cx + rx, cy + ry)

        val gyroAlpha = if (currentState == State.IDLE) 95 else 165
        val tiltColors = intArrayOf(c3, c2, c1)
        for (k in 0 until 3) {
            paintGyro.color = adjustAlpha(tiltColors[k], gyroAlpha)
            canvas.save()
            canvas.rotate(k * 60f + phase * (if (k % 2 == 0) 0.9f else -0.7f), cx, cy)
            canvas.drawOval(ovalRect, paintGyro)
            canvas.restore()
        }

        // Luminous hot neural center pinpoint
        paintGlow.shader = RadialGradient(
            cx, cy, sphereR * 0.34f,
            intArrayOf(
                Color.argb((190 + voiceBoost * 65).toInt().coerceIn(170, 255), 255, 255, 255),
                adjustAlpha(c3, 140),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, sphereR * 0.34f, paintGlow)

        // Subtle curved top-left glass specular sheen (luxury 3D lens look)
        paintHighlight.shader = RadialGradient(
            cx - sphereR * 0.32f, cy - sphereR * 0.36f, sphereR * 0.62f,
            intArrayOf(Color.argb(95, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - sphereR * 0.25f, cy - sphereR * 0.28f, sphereR * 0.56f, paintHighlight)

        canvas.restore()

        // 6. Razor-Thin Iridescent Fresnel Glass Rim
        paintFresnel.strokeWidth = maxOf(1.5f, minDim * 0.0075f)
        val rimShader = SweepGradient(
            cx, cy,
            intArrayOf(
                adjustAlpha(c1, 230),
                adjustAlpha(c3, 210),
                adjustAlpha(c2, 230),
                adjustAlpha(c1, 230)
            ),
            null
        )
        sweepMatrix.setRotate(phase * 1.6f, cx, cy)
        rimShader.setLocalMatrix(sweepMatrix)
        paintFresnel.shader = rimShader
        canvas.drawCircle(cx, cy, sphereR, paintFresnel)
    }

    private fun drawGradientTailArc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        rotationDeg: Float,
        color: Int,
        strokeW: Float,
        alpha: Int
    ) {
        paintHudArc.strokeWidth = strokeW
        val shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.TRANSPARENT,
                adjustAlpha(color, (alpha * 0.35f).toInt()),
                adjustAlpha(color, alpha),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.22f, 0.36f, 0.40f)
        )
        sweepMatrix.setRotate(rotationDeg, cx, cy)
        shader.setLocalMatrix(sweepMatrix)
        paintHudArc.shader = shader
        canvas.drawCircle(cx, cy, radius, paintHudArc)
    }

    private fun drawAuroraBlob(
        canvas: Canvas,
        bx: Float,
        by: Float,
        radius: Float,
        color: Int,
        alpha: Int
    ) {
        paintBlob.shader = RadialGradient(
            bx, by, radius,
            intArrayOf(
                adjustAlpha(color, alpha),
                adjustAlpha(color, (alpha * 0.45f).toInt()),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.52f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(bx, by, radius, paintBlob)
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255) and 0xFF) shl 24)
    }
}
