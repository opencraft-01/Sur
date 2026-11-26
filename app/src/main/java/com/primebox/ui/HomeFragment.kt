package com.primebox.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.primebox.data.Card
import com.primebox.data.HomeCache
import com.primebox.data.Store
import com.primebox.ext.Languages
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class HomeFragment : Fragment() {

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var rowsHost: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private lateinit var providerChip: TextView
    private lateinit var providerLabel: TextView
    private lateinit var carousel: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var heroWrap: FrameLayout

    private val rowAdapters = LinkedHashMap<String, PosterAdapter>()
    private val rowBlocks = LinkedHashMap<String, LinearLayout>()
    private val handler = Handler(Looper.getMainLooper())
    private var autoScroll: Runnable? = null
    private var currentProvider: String? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        refresh = SwipeRefreshLayout(context).apply {
            setColorSchemeColors(Ui.accent(context))
            setProgressBackgroundColorSchemeColor(Ui.surfaceHigh(context))
            setBackgroundColor(Ui.bg(context))
        }
        val scroll = NestedScrollView(context).apply {
            setBackgroundColor(Ui.bg(context))
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, Ui.dp(context, 96))
        }
        refresh.addView(scroll)
        refresh.setOnRefreshListener { loadHome(force = true) }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 24))
        }
        scroll.addView(column, ViewGroup.LayoutParams(-1, -2))

        // ---------------------------------------------------------- top bar
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 2))
        }
        Ui.applyTopInset(header, 8)
        val logoWrap = FrameLayout(context).apply { background = Ui.shape(context, 12, Ui.accentSoft(context)) }
        logoWrap.addView(
            ImageView(context).apply {
                setImageResource(com.primebox.R.drawable.ic_logo)
                setColorFilter(Ui.accent(context))
                setPadding(Ui.dp(context, 5), Ui.dp(context, 5), Ui.dp(context, 5), Ui.dp(context, 5))
            },
            FrameLayout.LayoutParams(Ui.dp(context, 30), Ui.dp(context, 30), Gravity.CENTER)
        )
        header.addView(logoWrap, LinearLayout.LayoutParams(Ui.dp(context, 30), Ui.dp(context, 30)))
        header.addView(
            Ui.display(context, "PrimeBox", 20f),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(context, 10) }
        )
        header.addView(Ui.iconButton(context, android.R.drawable.ic_menu_manage, Ui.text(context)) {
            startActivity(Intent(context, ExtensionsActivity::class.java))
        })
        header.addView(Ui.iconButton(context, android.R.drawable.ic_menu_preferences, Ui.text(context)) {
            startActivity(Intent(context, SettingsActivity::class.java))
        })
        column.addView(header)

        // ------------------------------------------------------- search bar
        val search = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 13), Ui.dp(context, 16), Ui.dp(context, 13))
            background = Ui.ripple(context, Ui.shape(context, 100, Ui.surface(context), 1, Ui.stroke(context)))
            setOnClickListener { startActivity(Intent(context, SearchActivity::class.java)) }
        }
        search.addView(
            ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_search)
                setColorFilter(Ui.textDim(context))
            },
            LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18))
        )
        search.addView(
            Ui.textView(context, "Search movies, series, anime…", 13.5f, Ui.textDim(context)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(context, 10) }
        )
        column.addView(search, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), 0)
        })

        // --------------------------------------------- provider + language
        val providerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), 0)
        }
        providerChip = Ui.pill(context, "Loading…", false) { openSwitcher() }
        providerRow.addView(providerChip)
        providerLabel = Ui.label(context, "")
        providerRow.addView(providerLabel, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = Ui.dp(context, 10) })
        column.addView(providerRow)

        // ------------------------------------------------------ row chips
        chipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), Ui.dp(context, 2))
        }
        column.addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        })

        // --------------------------------------------------------- carousel
        heroWrap = FrameLayout(context).apply { visibility = View.GONE }
        carousel = ViewPager2(context).apply {
            adapter = HeroAdapter(emptyList(), { }, { })
            offscreenPageLimit = 2
            clipToPadding = false
            clipChildren = false
            setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
        }
        carousel.setPageTransformer { page, position ->
            val clamped = position.coerceIn(-1f, 1f)
            page.scaleY = 1f - (kotlin.math.abs(clamped) * 0.06f)
            page.alpha = 1f - (kotlin.math.abs(clamped) * 0.35f)
            page.translationX = page.width * -0.06f * clamped
        }
        heroWrap.addView(carousel, FrameLayout.LayoutParams(-1, -1))
        column.addView(heroWrap, LinearLayout.LayoutParams(-1, Ui.dp(context, 300)).apply {
            topMargin = Ui.dp(context, 8)
        })
        dots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 10), 0, 0)
        }
        column.addView(dots)

        rowsHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(rowsHost)

        loadHome()
        maybeCheckUpdate()
        return refresh
    }

    /** Silent update check (max once every 6 hours) that opens the update popup. */
    private fun maybeCheckUpdate() {
        val context = context ?: return
        val now = System.currentTimeMillis()
        if (now - Store.lastUpdateCheck < 6L * 60L * 60L * 1000L) return
        Store.lastUpdateCheck = now
        viewLifecycleOwner.lifecycleScope.launch {
            val info = com.primebox.data.Updater.check() ?: return@launch
            if (info.versionCode <= com.primebox.data.Updater.currentCode()) return@launch
            if (!isAdded) return@launch
            if (Store.lastPromptedUpdate >= info.versionCode) return@launch
            Store.lastPromptedUpdate = info.versionCode
            val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return@launch
            UpdateSheet(activity, info).show()
        }
    }

    private fun openSwitcher() {
        (activity as? MainActivity)?.openSwitcher()
    }

    private fun openDetail(card: Card) {
        val context = context ?: return
        startActivity(
            Intent(context, DetailActivity::class.java)
                .putExtra("url", card.url)
                .putExtra("name", card.name)
                .putExtra("poster", card.poster)
                .putExtra("api", card.api ?: Providers.active()?.name)
        )
    }

    // ------------------------------------------------------------- skeleton

    private fun showSkeleton() {
        val context = context ?: return
        if (rowsHost.childCount > 0) return
        rowsHost.removeAllViews()
        repeat(3) {
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), 0)
            }
            block.addView(skeletonBlock(context, 150, 16, 8))
            val strip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(context, 12), 0, 0)
            }
            repeat(3) {
                strip.addView(skeletonBlock(context, 120, 176, 18))
                strip.addView(Ui.horizontalSpace(context, 12))
            }
            block.addView(strip)
            rowsHost.addView(block)
        }
    }

    // ---------------------------------------------------------------- load

    private fun loadHome(force: Boolean = false, attempt: Int = 0) {
        val context = context ?: return
        val api: MainAPI? = Providers.active()

        if (api == null) {
            providerChip.text = if (attempt < 12) "Loading extensions…" else "No extension"
            if (attempt < 12) {
                rowsHost.postDelayed({ if (isAdded) loadHome(force, attempt + 1) }, 400)
            } else {
                showEmptyState()
                refresh.isRefreshing = false
            }
            return
        }

        val providerName = api.name
        providerChip.text = "${Languages.flag(api.lang)}  ${api.name}"
        providerLabel.text = Languages.name(api.lang).uppercase()
        currentProvider = providerName

        // 1) show cached content instantly
        val cached = HomeCache.load(context, providerName)
        if (cached != null && cached.rows.isNotEmpty()) {
            renderRows(cached.rows, fromCache = true)
        } else {
            showSkeleton()
        }
        if (!force && cached != null && System.currentTimeMillis() - cached.time < 3 * 60 * 1000) {
            // fresh enough — skip the network call
            refresh.isRefreshing = false
            return
        }

        // 2) refresh from the network, row by row
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val page = api.mainPage.firstOrNull() ?: MainPageData("", "", false)
                    withTimeout(25_000) {
                        api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                    }
                }
            }
            if (!isAdded) return@launch
            refresh.isRefreshing = false

            val response = result.getOrNull()
            if (response == null || response.items.isEmpty()) {
                if (rowsHost.childCount == 0) showEmptyState()
                return@launch
            }

            val rows = response.items.map { list ->
                HomeCache.Row(list.name, list.list.map { Card.from(it) }, list.isHorizontalImages)
            }.sortedBy { rowPriority(it.name) }
            rows.forEach { row ->
                applyRow(row)
                kotlinx.coroutines.delay(90)
            }
            buildChips(rows.map { it.name })
            if (rows.isNotEmpty()) setHero(rows.first().items)
            HomeCache.save(context, providerName, rows)
        }
    }

    private fun buildChips(names: List<String>) {
        val context = context ?: return
        chipsRow.removeAllViews()
        names.forEachIndexed { index, name ->
            chipsRow.addView(Ui.pill(context, name, index == 0) {
                rowBlocks[name]?.let { target ->
                    (view as? SwipeRefreshLayout)?.let { sw ->
                        (sw.getChildAt(0) as? NestedScrollView)?.smoothScrollTo(0, target.top)
                    }
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(context, 8) })
        }
    }

    private fun renderRows(rows: List<HomeCache.Row>, fromCache: Boolean) {
        rowsHost.removeAllViews()
        rowAdapters.clear()
        rowBlocks.clear()
        rows.forEach { applyRow(it) }
        buildChips(rows.map { it.name })
        if (rows.isNotEmpty()) setHero(rows.first().items)
        if (fromCache && rows.isNotEmpty()) refresh.isRefreshing = false
    }

    /** Adds a row, or updates it in place when it already exists (incremental refresh). */
    private fun applyRow(row: HomeCache.Row) {
        val context = context ?: return
        val existing = rowAdapters[row.name]
        if (existing != null) {
            existing.submit(row.items)
            return
        }
        val wide = row.horizontal
        val cardW = if (wide) 200 else 114
        val cardH = if (wide) 114 else 180

        val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 20), Ui.dp(context, 8))
        }
        header.addView(
            Ui.textView(context, row.name, 15f, Ui.text(context), bold = true, maxLines = 1),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        header.addView(Ui.textView(context, "${row.items.size}  ›", 12f, Ui.textDim(context)))
        block.addView(header)

        val adapter = PosterAdapter(row.items, cardW, cardH) { openDetail(it) }
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            this.adapter = adapter
            setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 4), 0)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            itemAnimator = null
        }
        block.addView(recycler, LinearLayout.LayoutParams(-1, Ui.dp(context, cardH + 12)))
        rowsHost.addView(block)
        rowAdapters[row.name] = adapter
        rowBlocks[row.name] = block

        row.items.take(6).forEach { ImageLoaderPrefetch(it) }
    }

    /** Trending / language rows float to the top, like CloudStream's ordered home. */
    private fun rowPriority(name: String): Int {
        val n = name.lowercase()
        var score = 100
        if (n.contains("trending") || n.contains("top") || n.contains("popular") || n.contains("hot")) score -= 40
        if (n.contains("new") || n.contains("latest") || n.contains("recent") || n.contains("release")) score -= 20
        if (n.contains("hindi")) score -= 16
        if (n.contains("bengali") || n.contains("bangla")) score -= 15
        if (n.contains("tamil") || n.contains("telugu") || n.contains("malayalam") || n.contains("kannada")) score -= 13
        if (n.contains("dubbed") || n.contains("web series")) score -= 6
        if (n.contains("movie")) score -= 5
        if (n.contains("anime")) score += 4
        return score
    }

    private fun ImageLoaderPrefetch(card: Card) {
        com.primebox.img.ImageLoader.prefetch(card.poster, null, 400)
    }

    private fun setHero(cards: List<Card>) {
        val context = context ?: return
        val items = cards.take(8)
        if (items.isEmpty()) return
        heroWrap.visibility = View.VISIBLE
        carousel.adapter = HeroAdapter(items, { openDetail(it) }, { openDetail(it) })
        buildDots(items.size)
        startAutoScroll(items.size)
        // history row sits under the carousel
        addContinueWatching()
    }

    private fun addContinueWatching() {
        val context = context ?: return
        if (rowAdapters.containsKey("Continue watching")) return
        val history = Store.history().take(10)
        if (history.isEmpty()) return
        val cards = history.map {
            Card(it.title, it.url, it.poster, null, null, it.provider)
        }
        applyRow(HomeCache.Row("Continue watching", cards))
    }

    private fun buildDots(count: Int) {
        val context = context ?: return
        dots.removeAllViews()
        if (count <= 1) return
        repeat(count) { index ->
            val dot = View(context).apply {
                background = Ui.shape(context, 100, if (index == 0) Ui.accent(context) else Ui.stroke(context))
            }
            dots.addView(
                dot,
                LinearLayout.LayoutParams(if (index == 0) Ui.dp(context, 18) else Ui.dp(context, 6), Ui.dp(context, 6)).apply {
                    rightMargin = Ui.dp(context, 6)
                }
            )
        }
        carousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dots.childCount) {
                    val child = dots.getChildAt(i)
                    child.background = Ui.shape(context, 100, if (i == position) Ui.accent(context) else Ui.stroke(context))
                    child.layoutParams = (child.layoutParams as LinearLayout.LayoutParams).apply {
                        width = Ui.dp(context, if (i == position) 18 else 6)
                    }
                    child.requestLayout()
                }
            }
        })
    }

    private fun startAutoScroll(count: Int) {
        stopAutoScroll()
        if (count <= 1) return
        val task = object : Runnable {
            override fun run() {
                if (!isAdded) return
                carousel.setCurrentItem((carousel.currentItem + 1) % count, true)
                handler.postDelayed(this, 5000)
            }
        }
        autoScroll = task
        handler.postDelayed(task, 5000)
    }

    private fun stopAutoScroll() {
        autoScroll?.let { handler.removeCallbacks(it) }
        autoScroll = null
    }

    private fun showEmptyState() {
        val context = context ?: return
        rowsHost.removeAllViews()
        chipsRow.removeAllViews()
        heroWrap.visibility = View.GONE
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 28), Ui.dp(context, 40), Ui.dp(context, 28), Ui.dp(context, 24))
        }
        box.addView(Ui.display(context, "Nothing loaded yet", 22f))
        box.addView(
            Ui.body(context, "Add an extension repository or import a .cs3 file, then pick a source below."),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 8) }
        )
        box.addView(Ui.spacer(context, 18))
        box.addView(Ui.filledButton(context, "Open extensions") {
            startActivity(Intent(context, ExtensionsActivity::class.java))
        })
        rowsHost.addView(box)
    }

    override fun onResume() {
        super.onResume()
        if (!this::providerChip.isInitialized) return
        val name = Providers.active()?.name ?: ""
        if (name.isBlank() || name != currentProvider) {
            currentProvider = null
            loadHome(force = true)
        } else {
            startAutoScroll(carousel.adapter?.itemCount ?: 0)
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoScroll()
    }
}
