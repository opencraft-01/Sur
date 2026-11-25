package com.primebox.ui

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.NestedScrollView
import com.primebox.data.Store

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val scroll = NestedScrollView(this).apply { setBackgroundColor(Ui.bg(context)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 32))
        }
        scroll.addView(column)

        column.addView(Ui.textView(context, "Settings", 24f, Ui.text(context), bold = true))
        column.addView(Ui.spacer(context, 18))

        // theme
        column.addView(sectionTitle("Appearance"))
        val themeOptions = listOf("Follow system", "Light", "Dark")
        themeOptions.forEachIndexed { index, label ->
            val selected = Store.themeMode == index
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(context, 12).toFloat()
                    setColor(if (selected) Ui.surfaceHigh(context) else Ui.surface(context))
                    setStroke(Ui.dp(context, 1), if (selected) Ui.accent(context) else Ui.stroke(context))
                }
                isClickable = true
                setOnClickListener {
                    Store.themeMode = index
                    AppCompatDelegate.setDefaultNightMode(
                        when (index) {
                            1 -> AppCompatDelegate.MODE_NIGHT_NO
                            2 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                    recreate()
                }
            }
            row.addView(Ui.textView(context, label, 14f, Ui.text(context), bold = selected))
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 8))
            })
        }

        column.addView(Ui.spacer(context, 18))
        column.addView(sectionTitle("Playback"))
        val autoplay = Switch(context).apply {
            text = "  Autoplay next episode"
            setTextColor(Ui.text(context))
            isChecked = Store.autoplayNext
            setOnCheckedChangeListener { _, checked -> Store.autoplayNext = checked }
        }
        column.addView(autoplay)

        column.addView(Ui.spacer(context, 18))
        column.addView(sectionTitle("Data"))
        column.addView(Ui.pill(context, "Clear watch history", false) {
            Store.clearHistory()
            android.widget.Toast.makeText(context, "History cleared", android.widget.Toast.LENGTH_SHORT).show()
        })

        column.addView(Ui.spacer(context, 18))
        column.addView(sectionTitle("About"))
        column.addView(
            Ui.textView(
                context,
                "PrimeBox 1.0.0\nExtension-compatible media centre. Extensions are third-party code — install only repositories you trust.",
                12f,
                Ui.textDim(context)
            )
        )

        setContentView(scroll)
    }

    private fun sectionTitle(value: String): android.widget.TextView =
        Ui.textView(this, value.uppercase(), 11f, Ui.accent(this), bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        }
}
