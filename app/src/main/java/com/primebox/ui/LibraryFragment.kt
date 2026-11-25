package com.primebox.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.primebox.data.Downloader
import com.primebox.data.Store
import java.io.File

class LibraryFragment : Fragment() {

    private var mode = 0 // 0 = downloads, 1 = history
    private lateinit var listHost: LinearLayout
    private lateinit var downloadsCard: LinearLayout
    private lateinit var historyCard: LinearLayout
    private lateinit var downloadsCount: TextView
    private lateinit var historyCount: TextView
    private lateinit var emptyLabel: TextView

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val scroll = NestedScrollView(context).apply {
            setBackgroundColor(Ui.bg(context))
            clipToPadding = false
            setPadding(0, 0, 0, Ui.dp(context, 96))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 20), Ui.dp(context, 24))
        }
        Ui.applyTopInset(column, 8)
        scroll.addView(column)

        column.addView(Ui.display(context, "Library", 28f))
        column.addView(Ui.body(context, "Your downloads and watch history"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(context, 4) })
        column.addView(Ui.spacer(context, 18))

        // ------------------------------------------------------- stat cards
        val twin = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        downloadsCard = statCard(context, "Downloads", "Offline copies") { mode = 0; render() }
        historyCard = statCard(context, "History", "Recently played") { mode = 1; render() }
        downloadsCount = downloadsCard.getChildAt(1) as TextView
        historyCount = historyCard.getChildAt(1) as TextView
        twin.addView(downloadsCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Ui.dp(context, 8)
        })
        twin.addView(historyCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(context, 8)
        })
        column.addView(twin)

        column.addView(Ui.spacer(context, 22))
        emptyLabel = Ui.body(context, "")
        column.addView(emptyLabel)
        listHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(listHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 10)
        })

        render()
        return scroll
    }

    private fun statCard(context: android.content.Context, title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16))
            background = Ui.ripple(context, Ui.shape(context, 20, Ui.surface(context), 1, Ui.stroke(context)))
            isClickable = true
            setOnClickListener { onClick() }
        }
        card.addView(Ui.label(context, title))
        card.addView(Ui.textView(context, "0", 26f, Ui.text(context), bold = true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(context, 6) })
        card.addView(Ui.textView(context, subtitle, 11.5f, Ui.textDim(context)))
        return card
    }

    private fun paintCard(card: LinearLayout, selected: Boolean) {
        val context = context ?: return
        card.background = Ui.ripple(
            context,
            Ui.shape(
                context, 20,
                if (selected) Ui.accentSoft(context) else Ui.surface(context),
                1,
                if (selected) Ui.accent(context) else Ui.stroke(context)
            )
        )
    }

    private fun render() {
        val context = context ?: return
        paintCard(downloadsCard, mode == 0)
        paintCard(historyCard, mode == 1)

        val downloads = Downloader.all()
        val history = Store.history()
        downloadsCount.text = downloads.size.toString()
        historyCount.text = history.size.toString()

        listHost.removeAllViews()
        if (mode == 0) {
            if (downloads.isEmpty()) {
                emptyLabel.text = "Nothing downloaded yet. Open a title, load its sources and tap the download icon."
                return
            }
            emptyLabel.text = ""
            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = DownloadAdapter(downloads, { item ->
                    val path = item.filePath
                    if (item.state == Downloader.State.DONE && path != null && File(path).exists()) {
                        startActivity(
                            Intent(context, PlayerActivity::class.java)
                                .putExtra("url", Uri.fromFile(File(path)).toString())
                                .putExtra("title", item.title)
                                .putExtra("local", true)
                        )
                    } else if (item.state == Downloader.State.FAILED) {
                        Downloader.start(context, item)
                    } else {
                        Toast.makeText(context, "Still downloading…", Toast.LENGTH_SHORT).show()
                    }
                }, { item ->
                    Downloader.delete(item)
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                })
                isNestedScrollingEnabled = false
            }
            listHost.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            if (history.isEmpty()) {
                emptyLabel.text = "Watch something and it shows up here."
                return
            }
            emptyLabel.text = ""
            val list = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = HistoryAdapter(history) { entry ->
                    startActivity(
                        Intent(context, DetailActivity::class.java)
                            .putExtra("url", entry.url)
                            .putExtra("name", entry.title)
                            .putExtra("poster", entry.poster)
                            .putExtra("api", entry.provider)
                    )
                }
                isNestedScrollingEnabled = false
            }
            listHost.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            listHost.addView(Ui.spacer(context, 14))
            listHost.addView(Ui.tonalButton(context, "Clear history") {
                Store.clearHistory()
                render()
            })
        }
    }

    override fun onResume() {
        super.onResume()
        Downloader.listen { activity?.runOnUiThread { if (isAdded) render() } }
        render()
    }

    override fun onPause() {
        super.onPause()
        Downloader.listen(null)
    }
}
