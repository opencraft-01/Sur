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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.primebox.data.Downloader
import com.primebox.data.Store
import com.primebox.ext.Providers
import com.primebox.img.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var backdrop: ImageView
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var sourcesBox: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private var loaded: LoadResponse? = null
    private var provider: MainAPI? = null
    private var linksLoaded = false
    private var currentTitle: String = ""

    private val titleArg: String by lazy { intent.getStringExtra("name") ?: "" }
    private val urlArg: String by lazy { intent.getStringExtra("url") ?: "" }
    private val posterArg: String? by lazy { intent.getStringExtra("poster") }
    private val apiArg: String? by lazy { intent.getStringExtra("api") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        currentTitle = titleArg
        provider = Providers.all().firstOrNull { it.name == apiArg } ?: Providers.active()

        val scroll = NestedScrollView(this).apply {
            setBackgroundColor(Ui.bg(context))
            clipToPadding = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 40))
        }
        scroll.addView(column)

        // ------------------------------------------------------------ backdrop
        val heroHeight = (resources.displayMetrics.widthPixels * 0.62f).toInt()
        val hero = FrameLayout(context)
        backdrop = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Ui.surfaceHigh(context))
        }
        hero.addView(backdrop, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heroHeight + Ui.dp(context, 120)))
        hero.addView(
            View(context).apply { background = Ui.bottomFade() },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heroHeight + Ui.dp(context, 120))
        )
        hero.addView(
            View(context).apply { background = Ui.topFade() },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 120), Gravity.TOP)
        )
        val back = Ui.iconButton(context, android.R.drawable.ic_menu_revert, Color.WHITE) { finish() }
        hero.addView(back, FrameLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.TOP or Gravity.START).apply {
            topMargin = Ui.dp(context, 8)
            leftMargin = Ui.dp(context, 8)
        })
        Ui.applyTopInset(back, 4)
        column.addView(hero, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heroHeight))
        ImageLoader.load(posterArg, backdrop, targetPx = 900)

        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            backdrop.translationY = scrollY * 0.35f
            backdrop.alpha = (1f - (scrollY / (heroHeight.toFloat() * 1.2f))).coerceIn(0.35f, 1f)
        }

        // --------------------------------------------------------------- body
        body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0)
            translationY = -Ui.dp(context, 24).toFloat()
        }
        column.addView(body)

        body.addView(Ui.display(context, titleArg, 26f))
        status = Ui.label(context, "LOADING")
        body.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 8)
        })

        actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        body.addView(actionsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 16)
        })

        sourcesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(sourcesBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 20)
        })

        setContentView(scroll)
        loadDetails()
    }

    private fun chipRow(items: List<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        items.filter { it.isNotBlank() }.forEach { value ->
            row.addView(Ui.badge(this, value))
            row.addView(Ui.horizontalSpace(this, 6))
        }
        return row
    }

    private fun loadDetails() {
        val api = provider
        if (api == null) {
            status.text = "NO EXTENSION AVAILABLE"
            return
        }
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) { runCatching { api.load(urlArg) }.getOrNull() }
            if (response == null) {
                status.text = "COULD NOT LOAD THIS TITLE"
                return@launch
            }
            loaded = response
            status.text = "DETAILS"
            val meta = mutableListOf<String>()
            response.year?.let { meta.add(it.toString()) }
            meta.add(response.type.name)
            response.duration?.takeIf { it > 0 }?.let { meta.add("$it min") }
            response.contentRating?.let { meta.add(it) }
            body.addView(chipRow(meta), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@DetailActivity, 10)
            })

            response.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                body.addView(
                    Ui.body(this@DetailActivity, plot),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Ui.dp(this@DetailActivity, 14)
                    }
                )
            }

            when (response) {
                is MovieLoadResponse -> {
                    actionsRow.addView(Ui.filledButton(this@DetailActivity, "▶  Play") {
                        fetchLinks(response.dataUrl, response.name)
                    })
                    actionsRow.addView(Ui.horizontalSpace(this@DetailActivity, 10))
                    actionsRow.addView(Ui.tonalButton(this@DetailActivity, "Sources") {
                        fetchLinks(response.dataUrl, response.name)
                    })
                    fetchLinks(response.dataUrl, response.name, silent = true)
                }

                is TvSeriesLoadResponse -> addEpisodes(response.episodes, response.name)
                is AnimeLoadResponse -> addEpisodes(response.episodes.values.flatten(), response.name)
                else -> {
                    val dataUrl = runCatching {
                        response.javaClass.getMethod("getDataUrl").invoke(response) as? String
                    }.getOrNull()
                    if (dataUrl != null) {
                        actionsRow.addView(Ui.filledButton(this@DetailActivity, "▶  Play") {
                            fetchLinks(dataUrl, response.name)
                        })
                        fetchLinks(dataUrl, response.name, silent = true)
                    }
                }
            }
        }
    }

    private fun addEpisodes(episodes: List<Episode>, title: String) {
        if (episodes.isEmpty()) return
        body.addView(Ui.sectionHeader(this, "Episodes", "${episodes.size} total"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setPadding(0, Ui.dp(this@DetailActivity, 20), 0, Ui.dp(this@DetailActivity, 6))
        })
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = EpisodeAdapter(episodes) { episode, _ ->
                currentTitle = "$title — ${episode.name ?: "Episode " + episode.episode}"
                fetchLinks(episode.data, currentTitle)
            }
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        body.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun fetchLinks(data: String, title: String, silent: Boolean = false) {
        val api = provider ?: return
        if (linksLoaded && silent) return
        if (!silent) status.text = "FINDING SOURCES…"
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
                collected.sortedByDescending { it.quality }
            }
            if (isFinishing) return@launch
            status.text = if (links.isEmpty()) "NO PLAYABLE SOURCE" else ""
            if (links.isEmpty()) {
                if (!silent) Toast.makeText(this@DetailActivity, "No sources found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            linksLoaded = true
            renderSources(links, title)
            if (!silent) showLinkSheet(links, title)
        }
    }

    private fun renderSources(links: List<ExtractorLink>, title: String) {
        sourcesBox.removeAllViews()
        sourcesBox.addView(Ui.sectionHeader(this, "Sources", "${links.size} found"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setPadding(0, 0, 0, Ui.dp(this@DetailActivity, 6)) })
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = LinkAdapter(links, { play(it, title) }, { download(it, title) })
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        sourcesBox.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun play(link: ExtractorLink, title: String) {
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
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", link.url)
                .putExtra("referer", link.referer)
                .putExtra("title", title)
                .putExtra("headers", HashMap(link.headers))
        )
    }

    private fun download(link: ExtractorLink, title: String) {
        if (link.type == ExtractorLinkType.MAGNET || link.type == ExtractorLinkType.TORRENT) {
            Toast.makeText(this, "Torrent downloads are not supported", Toast.LENGTH_SHORT).show()
            return
        }
        val id = Integer.toHexString(link.url.hashCode())
        val item = Downloader.Item(
            id = id,
            title = title,
            poster = posterArg ?: loaded?.posterUrl,
            url = link.url,
            referer = link.referer,
            headers = link.headers,
            type = if (link.type == ExtractorLinkType.M3U8) "M3U8" else "VIDEO",
            quality = link.quality,
            provider = provider?.name ?: ""
        )
        Downloader.start(this, item)
        Toast.makeText(this, "Download started — see Library", Toast.LENGTH_SHORT).show()
    }

    private fun showLinkSheet(links: List<ExtractorLink>, title: String) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@DetailActivity, 16), Ui.dp(this@DetailActivity, 10), Ui.dp(this@DetailActivity, 16), Ui.dp(this@DetailActivity, 24))
            setBackgroundColor(Ui.surface(this@DetailActivity))
        }
        val handle = View(this).apply { background = Ui.shape(this@DetailActivity, 100, Ui.stroke(this@DetailActivity)) }
        column.addView(handle, LinearLayout.LayoutParams(Ui.dp(this@DetailActivity, 36), Ui.dp(this@DetailActivity, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Ui.dp(this@DetailActivity, 14)
        })
        column.addView(Ui.headline(this, "Pick a source"))
        column.addView(Ui.body(this, title))
        column.addView(Ui.spacer(this, 12))
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = LinkAdapter(links, { link ->
                sheet.dismiss()
                play(link, title)
            }, { link ->
                sheet.dismiss()
                download(link, title)
            })
        }
        column.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@DetailActivity, 340)))
        sheet.setContentView(column)
        sheet.show()
    }
}
