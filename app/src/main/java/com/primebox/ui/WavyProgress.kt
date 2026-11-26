package com.primebox.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material-3 flavoured progress line: a smooth travelling wave that grows with the
 * download percentage. The wave keeps moving while a transfer is running.
 */
class WavyProgressView(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Ui.dp(context, 7).toFloat()
        color = Ui.surfaceHigh(context)
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = Ui.dp(context, 7).toFloat()
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.accent(context)
    }

    private val path = Path()
    private var target = 0f
    private var shown = 0f
    private var phase = 0f

    private val phaseAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1500L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Ui.dp(context, 22)
        )
    }

    fun setProgress(value: Float, animate: Boolean = true) {
        target = value.coerceIn(0f, 1f)
        if (!animate) {
            shown = target
            invalidate()
            if (target > 0f && target < 1f) startWave() else stopWave()
            return
        }
        ValueAnimator.ofFloat(shown, target).apply {
            duration = 480L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (target > 0f && target < 1f) startWave() else if (target >= 1f) stopWave()
    }

    fun startWave() {
        if (!phaseAnimator.isStarted) phaseAnimator.start()
    }

    fun stopWave() {
        if (phaseAnimator.isStarted) phaseAnimator.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (target > 0f && target < 1f) startWave()
    }

    override fun onDetachedFromWindow() {
        stopWave()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cy = h / 2f
        val inset = trackPaint.strokeWidth / 2f + 1f
        val startX = inset
        val endX = w - inset
        val usable = endX - startX
        if (usable <= 0f) return

        path.reset()
        path.moveTo(startX, cy)
        path.lineTo(endX, cy)
        canvas.drawPath(path, trackPaint)

        val fillEnd = startX + usable * shown
        if (fillEnd <= startX + 1f) return

        val amplitude = h * 0.17f
        val wavelength = Ui.dp(context, 44).toFloat()
        path.reset()
        path.moveTo(startX, cy)
        var x = startX
        while (x < fillEnd) {
            val y = cy + amplitude * sin((x / wavelength) * 2 * PI.toFloat() + phase)
            path.lineTo(x, y)
            x += 3f
        }
        val endY = cy + amplitude * sin((fillEnd / wavelength) * 2 * PI.toFloat() + phase)
        path.lineTo(fillEnd, endY)

        wavePaint.shader = LinearGradient(
            startX, 0f, endX, 0f,
            intArrayOf(
                Ui.withAlpha(Ui.accent(context), 0.55f),
                Ui.accent(context)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, wavePaint)
        canvas.drawCircle(fillEnd, endY, trackPaint.strokeWidth * 0.72f, dotPaint)
    }
}
