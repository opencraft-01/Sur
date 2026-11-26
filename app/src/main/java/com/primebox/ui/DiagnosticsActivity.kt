package com.primebox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.primebox.ext.ExtensionManager

class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(context))
            setPadding(Ui.dp(context, 18), Ui.dp(context, 14), Ui.dp(context, 18), Ui.dp(context, 20))
        }
        Ui.applyTopInset(root, 6)

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.iconButton(context, android.R.drawable.ic_menu_revert, Ui.text(context)) { finish() })
        top.addView(Ui.display(context, "Diagnostics", 22f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Ui.pill(context, "Copy", true) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PrimeBox diagnostics", ExtensionManager.diagnosticsText(context)))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        })
        root.addView(top)

        val text = TextView(context).apply {
            text = ExtensionManager.diagnosticsText(context)
            setTextColor(Ui.textDim(context))
            textSize = 10.5f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, Ui.dp(context, 14), 0, 0)
        }
        val scroll = ScrollView(context).apply { addView(text) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }
}
