package com.primebox.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout

/** Animated skeleton placeholder used while thumbnails / rows load. */
class ShimmerView(context: Context, private val radiusDp: Int = 16) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(-0.4f, 1.4f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        val night = Ui.isNight(context)
        val base = if (night) 0xFF1A1A21.toInt() else 0xFFE7EAF0.toInt()
        val highlight = if (night) 0xFF272732.toInt() else 0xFFF6F8FB.toInt()
        paint.shader = LinearGradient(
            (progress - 0.35f) * w, 0f,
            progress * w, h,
            intArrayOf(base, highlight, base),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val r = Ui.dp(context, radiusDp).toFloat()
        canvas.drawRoundRect(rect, r, r, paint)
    }
}

/** Fixed-size shimmer block for skeleton screens. */
fun skeletonBlock(context: Context, widthDp: Int, heightDp: Int, radiusDp: Int = 16): View =
    ShimmerView(context, radiusDp).apply {
        layoutParams = LinearLayout.LayoutParams(Ui.dp(context, widthDp), Ui.dp(context, heightDp))
    }

/** Fills its parent (use with MATCH_PARENT layout params). */
fun skeletonFill(context: Context, radiusDp: Int = 16): View = ShimmerView(context, radiusDp)
