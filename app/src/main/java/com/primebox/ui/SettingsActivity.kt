package com.primebox.ui

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.primebox.BuildConfig
import com.primebox.data.Store

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val scroll = NestedScrollView(this).apply { setBackgroundColor(Ui.bg(context)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 20), Ui.dp(context, 40))
        }
        Ui.applyTopInset(column, 8)
        scroll.addView(column)

        column.addView(Ui.iconButton(context, android.R.drawable.ic_menu_revert, Ui.text(context)) { finish() })
        column.addView(Ui.display(context, "Settings", 28f), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(context, 6) })

        column.addView(Ui.spacer(context, 22))
        column.addView(Ui.sectionHeader(context, "Appearance").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })

        val themes = listOf("System" to 0, "Light" to 1, "Dark" to 2)
        val themeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        themes.forEach { (label, value) ->
            val selected = Store.themeMode == value
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 18))
                background = Ui.ripple(
                    context,
                    Ui.shape(
                        context, 18,
                        if (selected) Ui.accentSoft(context) else Ui.surface(context),
                        1,
                        if (selected) Ui.accent(context) else Ui.stroke(context)
                    )
                )
                isClickable = true
                setOnClickListener {
                    Store.themeMode = value
                    AppCompatDelegate.setDefaultNightMode(
                        when (value) {
                            1 -> AppCompatDelegate.MODE_NIGHT_NO
                            2 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                    recreate()
                }
            }
            card.addView(Ui.textView(context, label, 13f, if (selected) Ui.accent(context) else Ui.text(context), bold = true))
            themeRow.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (value != 2) rightMargin = Ui.dp(context, 8)
            })
        }
        column.addView(themeRow)

        column.addView(Ui.spacer(context, 24))
        column.addView(Ui.sectionHeader(context, "Playback").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        column.addView(rowCard(context, "Autoplay next episode", Store.autoplayNext) { Store.autoplayNext = it })

        column.addView(Ui.spacer(context, 24))
        column.addView(Ui.sectionHeader(context, "Updates").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        column.addView(Ui.body(context, "Installed: ${com.primebox.data.Updater.currentName()} (build ${com.primebox.data.Updater.currentCode()})"))

        val updateStatus = Ui.label(context, "")
        val updateButton = Ui.filledButton(context, "Check for updates") {
            lifecycleScope.launch {
                updateStatus.text = "CHECKING…"
                val info = com.primebox.data.Updater.check()
                if (info == null) {
                    updateStatus.text = "NO RELEASE FOUND"
                    return@launch
                }
                if (info.versionCode <= com.primebox.data.Updater.currentCode()) {
                    updateStatus.text = "UP TO DATE"
                    return@launch
                }
                updateStatus.text = "DOWNLOADING ${info.versionName}… 0%"
                val file = com.primebox.data.Updater.download(context, info) { pct ->
                    runOnUiThread { updateStatus.text = "DOWNLOADING ${info.versionName}… $pct%" }
                }
                if (file == null) {
                    updateStatus.text = "DOWNLOAD FAILED"
                    return@launch
                }
                updateStatus.text = "OPENING INSTALLER…"
                com.primebox.data.Updater.install(context, file)
            }
        }
        column.addView(updateButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 10)
        })
        column.addView(updateStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 8)
        })

        column.addView(Ui.spacer(context, 24))
        column.addView(Ui.sectionHeader(context, "Data").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        column.addView(Ui.tonalButton(context, "Clear watch history") {
            Store.clearHistory()
            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
        })
        column.addView(Ui.spacer(context, 10))
        column.addView(Ui.tonalButton(context, "Clear recent searches") {
            Store.clearRecentSearches()
            Toast.makeText(context, "Searches cleared", Toast.LENGTH_SHORT).show()
        })

        column.addView(Ui.spacer(context, 24))
        column.addView(Ui.sectionHeader(context, "About").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        column.addView(
            Ui.body(
                context,
                "PrimeBox ${BuildConfig.VERSION_NAME}\nExtension-driven media centre. Extensions are third-party code — install only repositories you trust."
            )
        )

        setContentView(scroll)
    }

    private fun rowCard(context: android.content.Context, label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10))
            background = Ui.shape(context, 16, Ui.surface(context), 1, Ui.stroke(context))
        }
        row.addView(Ui.textView(context, label, 14f, Ui.text(context)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = Switch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(toggle)
        return row
    }
}
