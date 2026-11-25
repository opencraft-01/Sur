package com.primebox.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView

/**
 * PrimeBox design system — Material 3 expressive leaning: big rounded shapes,
 * bold type scale, tonal surfaces, springy motion, one brand accent.
 */
object Ui {

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // ---------------------------------------------------------------- palette

    fun bg(context: Context): Int =
        if (isNight(context)) Color.parseColor("#0A0A0D") else Color.parseColor("#FFFFFF")

    fun surface(context: Context): Int =
        if (isNight(context)) Color.parseColor("#121217") else Color.parseColor("#F5F6F9")

    fun surfaceHigh(context: Context): Int =
        if (isNight(context)) Color.parseColor("#1A1A21") else Color.parseColor("#ECEEF3")

    fun surfaceHighest(context: Context): Int =
        if (isNight(context)) Color.parseColor("#23232C") else Color.parseColor("#E2E5EC")

    fun text(context: Context): Int =
        if (isNight(context)) Color.parseColor("#FFFFFF") else Color.parseColor("#0B0B0F")

    fun textDim(context: Context): Int =
        if (isNight(context)) Color.parseColor("#A0A4AE") else Color.parseColor("#5C6270")

    fun accent(context: Context): Int =
        if (isNight(context)) Color.parseColor("#00D98B") else Color.parseColor("#00A66B")

    fun onAccent(context: Context): Int =
        if (isNight(context)) Color.parseColor("#04120C") else Color.parseColor("#FFFFFF")

    fun stroke(context: Context): Int =
        if (isNight(context)) Color.parseColor("#2A2A34") else Color.parseColor("#DCE0E8")

    fun danger(context: Context): Int = Color.parseColor("#FF5C5C")

    fun accentSoft(context: Context): Int =
        if (isNight(context)) Color.parseColor("#14251F") else Color.parseColor("#E6F7EF")

    // ------------------------------------------------------------------ units

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
        ).toInt()

    // ------------------------------------------------------------- typography

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
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    /** Display / hero title — expressive scale. */
    fun display(context: Context, value: String, sizeSp: Float = 30f): TextView =
        textView(context, value, sizeSp, text(context), bold = true).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = -0.02f
        }

    fun headline(context: Context, value: String): TextView =
        textView(context, value, 20f, text(context), bold = true).apply { letterSpacing = -0.01f }

    fun title(context: Context, value: String): TextView =
        textView(context, value, 15f, text(context), bold = true)

    fun body(context: Context, value: String): TextView =
        textView(context, value, 13.5f, textDim(context))

    fun label(context: Context, value: String): TextView =
        textView(context, value.uppercase(), 10.5f, textDim(context), bold = true).apply {
            letterSpacing = 0.14f
        }

    // ---------------------------------------------------------------- shapes

    fun shape(context: Context, radiusDp: Int, color: Int, strokeDp: Int = 0, strokeColor: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color)
            if (strokeDp > 0) setStroke(dp(context, strokeDp), strokeColor)
        }

    fun ripple(context: Context, content: android.graphics.drawable.Drawable): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(withAlpha(if (isNight(context)) Color.WHITE else Color.BLACK, 0.14f)),
            content,
            null
        )

    fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )

    // ----------------------------------------------------------------- cards

    fun card(context: Context, radius: Int = 20, background: Int = surface(context)): MaterialCardView =
        MaterialCardView(context).apply {
            this.radius = dp(context, radius).toFloat()
            setCardBackgroundColor(background)
            strokeWidth = 0
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            rippleColor = ColorStateList.valueOf(withAlpha(if (isNight(context)) Color.WHITE else Color.BLACK, 0.12f))
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

    // --------------------------------------------------------------- scrims

    fun bottomFade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(Color.parseColor("#F70A0A0D"), Color.parseColor("#C00A0A0D"), Color.TRANSPARENT)
    )

    fun topFade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#B30A0A0D"), Color.TRANSPARENT)
    )

    fun sideFade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#E60A0A0D"), Color.TRANSPARENT)
    )

    // -------------------------------------------------------------- controls

    /** Filled pill button. */
    fun filledButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(onAccent(context))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(context, 22), dp(context, 13), dp(context, 22), dp(context, 13))
            background = ripple(context, shape(context, 100, accent(context)))
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** Tonal (subtle) pill button. */
    fun tonalButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(text(context))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 12))
            background = ripple(context, shape(context, 100, surfaceHigh(context), 1, stroke(context)))
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun pill(context: Context, label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (selected) onAccent(context) else textDim(context))
            typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9))
            background = ripple(
                context,
                shape(
                    context, 100,
                    if (selected) accent(context) else surfaceHigh(context),
                    if (selected) 0 else 1,
                    stroke(context)
                )
            )
            isClickable = true
            setOnClickListener {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                }.start()
                onClick()
            }
        }

    fun iconButton(context: Context, iconRes: Int, tint: Int = text(context), onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            background = ripple(context, shape(context, 100, Color.TRANSPARENT))
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(tint)
                val pad = dp(context, 10)
                setPadding(pad, pad, pad, pad)
            }, FrameLayout.LayoutParams(dp(context, 40), dp(context, 40), Gravity.CENTER))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 40))
        }

    fun playButton(context: Context, onClick: () -> Unit): FrameLayout =
        FrameLayout(context).apply {
            background = ripple(context, shape(context, 100, accent(context)))
            addView(ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(onAccent(context))
                val pad = dp(context, 12)
                setPadding(pad, pad, pad, pad)
            }, FrameLayout.LayoutParams(dp(context, 48), dp(context, 48), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
            isClickable = true
            setOnClickListener {
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
                onClick()
            }
        }

    /** Small rounded badge (quality / language / type). */
    fun badge(context: Context, label: String, color: Int = textDim(context), bgColor: Int = surfaceHighest(context)): TextView =
        TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(color)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
            background = shape(context, 8, bgColor)
        }

    // -------------------------------------------------------------- sections

    fun sectionHeader(
        context: Context,
        title: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ): LinearLayout {
        val row = row(context)
        row.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 10))
        row.addView(headline(context, title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null && onAction != null) {
            row.addView(textView(context, action, 12.5f, accent(context), bold = true).apply {
                setPadding(dp(context, 10), dp(context, 4), 0, dp(context, 4))
                setOnClickListener { onAction() }
            })
        }
        return row
    }

    // --------------------------------------------------------------- insets

    fun applyTopInset(view: View, extraDp: Int = 0) {
        val extra = dp(view.context, extraDp)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top + extra)
            insets
        }
    }

    fun applyBottomInset(view: View, extraDp: Int = 0) {
        val extra = dp(view.context, extraDp)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = bars.bottom + extra)
            insets
        }
    }

    // -------------------------------------------------------------- shimmer

    /** Skeleton placeholder block with a soft pulse. */
    fun skeleton(context: Context, widthDp: Int, heightDp: Int, radius: Int = 18): View =
        View(context).apply {
            background = shape(context, radius, surfaceHigh(context))
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp))
            animate().alpha(1f).setDuration(700).withEndAction {
                animate().alpha(0.55f).setDuration(700).withEndAction {
                    // single gentle pulse; avoid endless animation
                }.start()
            }.start()
        }
}
