package com.primebox.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.primebox.data.Store

class LibraryFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val scroll = NestedScrollView(context).apply { setBackgroundColor(Ui.bg(context)) }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 32))
        }
        scroll.addView(column)

        column.addView(Ui.textView(context, "Library", 24f, Ui.text(context), bold = true))
        column.addView(Ui.textView(context, "Your downloads and watch history", 12f, Ui.textDim(context)))
        column.addView(Ui.spacer(context, 16))

        val twin = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        column.addView(twin)

        // ---------------- downloads column
        val downloads = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 16).toFloat()
                setColor(Ui.surface(context))
                setStroke(Ui.dp(context, 1), Ui.stroke(context))
            }
        }
        downloads.addView(Ui.textView(context, "Downloads", 14f, Ui.text(context), bold = true))
        downloads.addView(Ui.spacer(context, 6))
        downloads.addView(Ui.textView(context, "Nothing downloaded yet", 12f, Ui.textDim(context)))
        downloads.addView(Ui.spacer(context, 10))
        downloads.addView(Ui.pill(context, "Coming soon", false) { })
        twin.addView(downloads, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Ui.dp(context, 6)
        })

        // ---------------- history column
        val historyBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 16).toFloat()
                setColor(Ui.surface(context))
                setStroke(Ui.dp(context, 1), Ui.stroke(context))
            }
        }
        historyBox.addView(Ui.textView(context, "History", 14f, Ui.text(context), bold = true))
        historyBox.addView(Ui.spacer(context, 6))

        val entries = Store.history()
        if (entries.isEmpty()) {
            historyBox.addView(Ui.textView(context, "Watch something and it shows up here", 12f, Ui.textDim(context)))
        } else {
            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = HistoryAdapter(entries) { entry ->
                    startActivity(
                        Intent(context, DetailActivity::class.java)
                            .putExtra("url", entry.url)
                            .putExtra("name", entry.title)
                            .putExtra("poster", entry.poster)
                            .putExtra("api", entry.provider)
                    )
                }
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            historyBox.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        twin.addView(historyBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(context, 6)
        })

        if (entries.isNotEmpty()) {
            column.addView(Ui.spacer(context, 16))
            column.addView(Ui.pill(context, "Clear history", false) {
                Store.clearHistory()
                (activity as? MainActivity)?.recreate()
            })
        }

        return scroll
    }
}
