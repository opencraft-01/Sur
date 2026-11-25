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
import com.primebox.data.Store
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var results: RecyclerView
    private lateinit var status: android.widget.TextView
    private lateinit var recentBox: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(context))
            setPadding(Ui.dp(context, 18), Ui.dp(context, 14), Ui.dp(context, 18), 0)
        }
        Ui.applyTopInset(column, 6)

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.iconButton(context, android.R.drawable.ic_menu_revert, Ui.text(context)) { finish() })
        top.addView(Ui.display(context, "Search", 22f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(context, 4)
        })
        column.addView(top)
        column.addView(Ui.spacer(context, 10))

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4))
            background = Ui.shape(context, 100, Ui.surface(context), 1, Ui.stroke(context))
        }
        input = EditText(context).apply {
            hint = "Search movies, series, anime…"
            setHintTextColor(Ui.textDim(context))
            setTextColor(Ui.text(context))
            setSingleLine()
            background = null
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        bar.addView(input, LinearLayout.LayoutParams(0, Ui.dp(context, 50), 1f))
        bar.addView(Ui.filledButton(context, "Go") { runSearch(input.text.toString()) })
        column.addView(bar)

        status = Ui.label(context, "")
        column.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 12)
        })

        recentBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(context, 12), 0, 0)
        }
        column.addView(recentBox)

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
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 24))
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
        renderRecent()
    }

    private fun renderRecent() {
        val context = this
        recentBox.removeAllViews()
        val recent = Store.recentSearches()
        if (recent.isEmpty()) return
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Ui.label(context, "Recent"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.textView(context, "Clear", 11.5f, Ui.accent(context), bold = true).apply {
            setOnClickListener {
                Store.clearRecentSearches()
                renderRecent()
            }
        })
        recentBox.addView(header)
        recent.forEach { query ->
            val chip = Ui.pill(context, query, false) { input.setText(query); runSearch(query) }
            recentBox.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(context, 8)
            })
        }
    }

    private fun runSearch(query: String) {
        val api = Providers.active()
        if (api == null) {
            status.text = "NO EXTENSION INSTALLED"
            return
        }
        if (query.isBlank()) return
        Store.addRecentSearch(query)
        recentBox.removeAllViews()
        status.text = "SEARCHING…"
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { api.search(query) }.getOrNull().orEmpty()
            }
            status.text = if (found.isEmpty()) "NO RESULTS" else "${found.size} RESULTS"
            (results.adapter as? PosterAdapter)?.submit(found)
        }
    }
}
