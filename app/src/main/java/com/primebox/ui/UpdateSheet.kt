package com.primebox.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.primebox.data.Updater
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Bottom popup shown when a new build is available. It downloads in place and
 * opens the system installer automatically once the APK is ready.
 */
class UpdateSheet(
    private val activity: AppCompatActivity,
    private val info: Updater.Info
) {

    private lateinit var progress: WavyProgressView
    private lateinit var stats: TextView
    private lateinit var action: TextView
    private lateinit var sheet: BottomSheetDialog
    private var busy = false

    fun show() {
        val context = activity
        sheet = BottomSheetDialog(context)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 22), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 26))
            setBackgroundColor(Ui.surface(context))
        }

        val handle = View(context).apply { background = Ui.shape(context, 100, Ui.stroke(context)) }
        column.addView(handle, LinearLayout.LayoutParams(Ui.dp(context, 38), Ui.dp(context, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Ui.dp(context, 18)
        })

        column.addView(Ui.display(context, "Update available", 22f))
        column.addView(
            Ui.textView(
                context,
                "PrimeBox ${info.versionName}  •  you have ${Updater.currentName()}",
                12.5f,
                Ui.textDim(context)
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 4) }
        )

        if (info.notes.isNotBlank()) {
            column.addView(
                Ui.textView(context, info.notes.trim(), 12f, Ui.textDim(context), maxLines = 6),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 12) }
            )
        }

        // ------------------------------------------------------- progress area
        progress = WavyProgressView(context).apply {
            visibility = View.INVISIBLE
        }
        column.addView(progress, LinearLayout.LayoutParams(-1, Ui.dp(context, 22)).apply {
            topMargin = Ui.dp(context, 20)
        })

        stats = Ui.textView(context, "", 12f, Ui.textDim(context)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.INVISIBLE
        }
        column.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 8) })

        action = Ui.filledButton(context, "Update now") { startDownload() }
        column.addView(action, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 18) })

        column.addView(
            Ui.textView(
                context,
                "The APK downloads inside the app and the installer opens automatically.",
                11f,
                Ui.textDim(context)
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 10) }
        )

        sheet.setContentView(column)
        sheet.show()
    }

    private fun startDownload() {
        if (busy) return
        busy = true
        val context = activity
        progress.visibility = View.VISIBLE
        stats.visibility = View.VISIBLE
        action.text = "Downloading…"
        action.alpha = 0.6f
        action.setOnClickListener(null)
        updateStats(0, 0L, 0L, 0L)

        activity.lifecycleScope.launch {
            val file = Updater.download(context, info) { p ->
                activity.runOnUiThread {
                    progress.setProgress(p.percent / 100f)
                    updateStats(p.percent, p.downloaded, p.total, p.bytesPerSecond)
                }
            }
            if (file == null) {
                action.text = "Retry download"
                action.alpha = 1f
                action.setOnClickListener { startDownload() }
                stats.text = "Download failed — check your connection"
                busy = false
                return@launch
            }
            progress.setProgress(1f)
            stats.text = "Downloaded — opening installer…"
            action.text = "Opening installer…"
            Updater.install(context, file)
            sheet.dismiss()
            busy = false
        }
    }

    private fun updateStats(percent: Int, downloaded: Long, total: Long, speed: Long) {
        stats.text = String.format(
            Locale.US,
            "%d%%   •   %s / %s   •   %s",
            percent,
            bytes(downloaded),
            bytes(total),
            if (speed > 0) "${bytes(speed)}/s" else "—"
        )
    }

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
}
