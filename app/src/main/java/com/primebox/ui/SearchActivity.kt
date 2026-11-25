package com.primebox.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var results: RecyclerView
    private lateinit var status: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(context))
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), 0)
        }

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 12), Ui.dp(context, 6), Ui.dp(context, 12), Ui.dp(context, 6))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 14).toFloat()
                setColor(Ui.surface(context))
                setStroke(Ui.dp(context, 1), Ui.stroke(context))
            }
        }
        val input = EditText(context).apply {
            hint = "Search…"
            setHintTextColor(Ui.textDim(context))
            setTextColor(Ui.text(context))
            setSingleLine()
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        bar.addView(input, LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1f))
        bar.addView(Ui.pill(context, "Search", true) { runSearch(input.text.toString()) })
        column.addView(bar)

        status = Ui.textView(context, "", 12f, Ui.textDim(context))
        column.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 10)
        })

        results = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = PosterAdapter(emptyList(), 104, 150) { item ->
                startActivity(
                    Intent(context, DetailActivity::class.java)
                        .putExtra("url", item.url)
                        .putExtra("name", item.name)
                        .putExtra("poster", item.posterUrl)
                        .putExtra("api", item.apiName)
                )
            }
            setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 24))
            clipToPadding = false
        }
        column.addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(column)
        input.requestFocus()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(input.text.toString())
                true
            } else false
        }
    }

    private fun runSearch(query: String) {
        val api = Providers.active()
        if (api == null) {
            status.text = "No extension installed"
            return
        }
        if (query.isBlank()) return
        status.text = "Searching…"
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { api.search(query) }.getOrNull().orEmpty()
            }
            status.text = if (found.isEmpty()) "No results" else "${found.size} results"
            (results.adapter as? PosterAdapter)?.submit(found)
        }
    }
}
