package com.primebox.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.primebox.data.Store
import com.primebox.ext.Providers
import com.primebox.img.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var loaded: LoadResponse? = null
    private var provider: MainAPI? = null

    private val titleArg: String by lazy { intent.getStringExtra("name") ?: "" }
    private val urlArg: String by lazy { intent.getStringExtra("url") ?: "" }
    private val posterArg: String? by lazy { intent.getStringExtra("poster") }
    private val apiArg: String? by lazy { intent.getStringExtra("api") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        provider = Providers.all().firstOrNull { it.name == apiArg } ?: Providers.active()

        val scroll = NestedScrollView(this).apply { setBackgroundColor(Ui.bg(context)) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 32))
        }
        scroll.addView(root)

        // backdrop
        val backdrop = FrameLayout(context).apply { setBackgroundColor(Ui.surfaceHigh(context)) }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        backdrop.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        backdrop.addView(View(context).apply { background = Ui.bottomFade() }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ImageLoader.load(posterArg, image, targetPx = 800)

        val back = TextView(context).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 8))
            setOnClickListener { finish() }
        }
        backdrop.addView(back)
        root.addView(backdrop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 240)))

        body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), 0)
        }
        root.addView(body)

        body.addView(Ui.textView(context, titleArg, 22f, Ui.text(context), bold = true))
        status = Ui.textView(context, "Loading…", 12f, Ui.textDim(context))
        body.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 6)
        })

        setContentView(scroll)
        loadDetails()
    }

    private fun loadDetails() {
        val api = provider
        if (api == null) {
            status.text = "No extension available"
            return
        }
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching { api.load(urlArg) }.getOrNull()
            }
            if (response == null) {
                status.text = "Could not load this title"
                return@launch
            }
            loaded = response
            status.text = buildString {
                response.year?.let { append("$it  •  ") }
                append(response.type.name)
                response.duration?.let { append("  •  ${it} min") }
            }

            response.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                body.addView(
                    Ui.textView(this@DetailActivity, plot, 13f, Ui.textDim(this@DetailActivity), maxLines = 6),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Ui.dp(this@DetailActivity, 10)
                    }
                )
            }

            when (response) {
                is MovieLoadResponse -> addPlayButton(response.dataUrl, response.name)
                is TvSeriesLoadResponse -> addEpisodes(response.episodes, response.name, response.posterUrl)
                is AnimeLoadResponse -> addEpisodes(response.episodes.values.flatten(), response.name, response.posterUrl)
                else -> {
                    val dataUrl = runCatching {
                        response.javaClass.getMethod("getDataUrl").invoke(response) as? String
                    }.getOrNull()
                    if (dataUrl != null) addPlayButton(dataUrl, response.name)
                }
            }
        }
    }

    private fun addPlayButton(dataUrl: String, title: String) {
        val context = this
        body.addView(Ui.spacer(context, 16))
        val button = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 18), Ui.dp(context, 14), Ui.dp(context, 18), Ui.dp(context, 14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 14).toFloat()
                setColor(Ui.accent(context))
            }
            isClickable = true
            setOnClickListener { fetchLinks(dataUrl, title) }
        }
        button.addView(Ui.textView(context, "▶  Play", 15f, Color.parseColor("#04120C"), bold = true))
        body.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun addEpisodes(episodes: List<Episode>, title: String, poster: String?) {
        val context = this
        body.addView(Ui.spacer(context, 16))
        body.addView(Ui.textView(context, "Episodes", 15f, Ui.text(context), bold = true))
        body.addView(Ui.spacer(context, 8))
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = EpisodeAdapter(episodes) { episode ->
                fetchLinks(episode.data, "$title — ${episode.name ?: "Episode " + episode.episode}")
            }
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        body.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun fetchLinks(data: String, title: String) {
        val api = provider ?: return
        status.text = "Finding links…"
        lifecycleScope.launch {
            val links = withContext(Dispatchers.IO) {
                val collected = ArrayList<ExtractorLink>()
                runCatching {
                    api.loadLinks(
                        data = data,
                        isCasting = false,
                        subtitleCallback = { _: SubtitleFile -> },
                        callback = { link -> collected.add(link) }
                    )
                }
                collected
            }
            status.text = if (links.isEmpty()) "No playable links found" else ""
            if (links.isNotEmpty()) showLinkSheet(links, title)
        }
    }

    private fun showLinkSheet(links: List<ExtractorLink>, title: String) {
        val context = this
        val sheet = BottomSheetDialog(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 24))
            setBackgroundColor(Ui.surface(context))
        }
        column.addView(Ui.textView(context, "Select a stream", 16f, Ui.text(context), bold = true))
        column.addView(Ui.spacer(context, 10))
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = LinkAdapter(links) { link ->
                sheet.dismiss()
                Store.addHistory(
                    Store.HistoryEntry(
                        title = title,
                        poster = posterArg ?: loaded?.posterUrl,
                        provider = provider?.name ?: "",
                        url = urlArg,
                        time = System.currentTimeMillis()
                    )
                )
                startActivity(
                    Intent(context, PlayerActivity::class.java)
                        .putExtra("url", link.url)
                        .putExtra("referer", link.referer)
                        .putExtra("title", title)
                        .putExtra("headers", HashMap(link.headers))
                )
            }
        }
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 320)))
        sheet.setContentView(column)
        sheet.show()
    }
}
