package com.primebox.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

/**
 * Colour palette + small view builders so every screen is built in code
 * (no layout XML to keep in sync, and no XML crashes at runtime).
 */
object Ui {

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun bg(context: Context): Int = if (isNight(context)) Color.parseColor("#0B0B0F") else Color.parseColor("#FFFFFF")
    fun surface(context: Context): Int = if (isNight(context)) Color.parseColor("#16161C") else Color.parseColor("#F3F4F7")
    fun surfaceHigh(context: Context): Int = if (isNight(context)) Color.parseColor("#20202A") else Color.parseColor("#E8EAF0")
    fun text(context: Context): Int = if (isNight(context)) Color.parseColor("#FFFFFF") else Color.parseColor("#0B0B0F")
    fun textDim(context: Context): Int = if (isNight(context)) Color.parseColor("#9AA0A6") else Color.parseColor("#5F6368")
    fun accent(context: Context): Int = Color.parseColor("#00D98B")
    fun stroke(context: Context): Int = if (isNight(context)) Color.parseColor("#2A2A35") else Color.parseColor("#DDE1E8")
    fun danger(context: Context): Int = Color.parseColor("#FF5C5C")

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun textView(
        context: Context,
        value: String,
        sizeSp: Float = 14f,
        color: Int = text(context),
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ): TextView = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    fun card(context: Context, radius: Int = 14, background: Int = surface(context)): MaterialCardView =
        MaterialCardView(context).apply {
            this.radius = dp(context, radius).toFloat()
            setCardBackgroundColor(background)
            strokeWidth = 0
            cardElevation = 0f
            isClickable = true
            isFocusable = true
        }

    fun row(context: Context, gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            this.gravity = gravity
        }

    fun column(context: Context): LinearLayout =
        LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun spacer(context: Context, heightDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp))
    }

    fun horizontalSpace(context: Context, widthDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(context, widthDp), 1)
    }

    /** Dark fade used over hero art so white text stays readable. */
    fun bottomFade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(Color.parseColor("#F20B0B0F"), Color.parseColor("#B30B0B0F"), Color.TRANSPARENT)
    )

    fun topFade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#CC0B0B0F"), Color.TRANSPARENT)
    )

    fun pill(context: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (selected) Color.parseColor("#04120C") else text(context))
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 20).toFloat()
                setColor(if (selected) accent(context) else surface(context))
                setStroke(dp(context, 1), if (selected) accent(context) else stroke(context))
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun playButton(context: Context, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent(context))
            }
            addView(ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.parseColor("#04120C"))
                val pad = dp(context, 10)
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(dp(context, 44), dp(context, 44), Gravity.CENTER)
            })
            layoutParams = LinearLayout.LayoutParams(dp(context, 44), dp(context, 44))
            isClickable = true
            setOnClickListener { onClick() }
        }
}
