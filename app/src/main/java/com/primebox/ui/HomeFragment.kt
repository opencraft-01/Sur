package com.primebox.ui

import android.content.Intent
import android.graphics.Color
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
import com.lagradost.cloudstream3.SearchResponse
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var rows: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var carousel: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var providerLabel: TextView
    private lateinit var refresh: SwipeRefreshLayout
    private val rowAnchors = LinkedHashMap<String, View>()
    private val handler = Handler(Looper.getMainLooper())
    private var autoScroll: Runnable? = null

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
            setPadding(0, 0, 0, Ui.dp(context, 90))
        }
        refresh.addView(scroll)
        refresh.setOnRefreshListener { loadHome() }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, Ui.dp(context, 24))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ------------------------------------------------------------- header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 4))
        }
        Ui.applyTopInset(header, 8)
        val logoWrap = FrameLayout(context).apply {
            background = Ui.shape(context, 12, Ui.accentSoft(context))
        }
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
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(context, 10) }
        )
        header.addView(Ui.iconButton(context, android.R.drawable.ic_menu_manage, Ui.text(context)) {
            startActivity(Intent(context, ExtensionsActivity::class.java))
        })
        header.addView(Ui.iconButton(context, android.R.drawable.ic_menu_preferences, Ui.text(context)) {
            startActivity(Intent(context, SettingsActivity::class.java))
        })
        column.addView(header)

        // -------------------------------------------------------------- search
        val search = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 14))
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
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(context, 10) }
        )
        column.addView(
            search,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), 0)
            }
        )

        // ------------------------------------------------------- provider row
        val providerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 22), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 2))
        }
        val dot = View(context).apply { background = Ui.shape(context, 100, Ui.accent(context)) }
        providerRow.addView(dot, LinearLayout.LayoutParams(Ui.dp(context, 6), Ui.dp(context, 6)))
        providerLabel = Ui.label(context, "")
        providerRow.addView(
            providerLabel,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(context, 8) }
        )
        column.addView(providerRow)

        // ------------------------------------------------------------- chips
        chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), Ui.dp(context, 4))
        }
        column.addView(android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        })

        // ----------------------------------------------------------- carousel
        val heroWrap = FrameLayout(context)
        carousel = ViewPager2(context).apply {
            adapter = HeroAdapter(emptyList(), { }, { })
            offscreenPageLimit = 2
            clipToPadding = false
            clipChildren = false
        }
        carousel.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
        carousel.offscreenPageLimit = 2
        carousel.setPageTransformer { page, position ->
            val clamped = position.coerceIn(-1f, 1f)
            page.scaleY = 1f - (kotlin.math.abs(clamped) * 0.06f)
            page.alpha = 1f - (kotlin.math.abs(clamped) * 0.35f)
            page.translationX = page.width * -0.06f * clamped
        }
        heroWrap.addView(
            carousel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        column.addView(
            heroWrap,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 300)).apply {
                topMargin = Ui.dp(context, 6)
            }
        )

        dots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 10), 0, 0)
        }
        column.addView(dots)

        // --------------------------------------------------------------- rows
        rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(rows)

        loadHome()
        return refresh
    }

    private fun openDetail(item: SearchResponse) {
        val context = context ?: return
        startActivity(
            Intent(context, DetailActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("name", item.name)
                .putExtra("poster", item.posterUrl)
                .putExtra("api", item.apiName)
        )
    }

    private fun showSkeleton() {
        val context = context ?: return
        rows.removeAllViews()
        repeat(3) {
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), 0)
            }
            block.addView(Ui.skeleton(context, 140, 16, 8))
            val strip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(context, 12), 0, 0)
            }
            repeat(3) {
                strip.addView(Ui.skeleton(context, 120, 170, 18))
                strip.addView(Ui.horizontalSpace(context, 12))
            }
            block.addView(strip)
            rows.addView(block)
        }
    }

    private fun loadHome(attempt: Int = 0) {
        val context = context ?: return
        val api: MainAPI? = Providers.active()
        if (api == null) {
            if (attempt < 12) {
                providerLabel.text = "LOADING EXTENSIONS"
                rows.postDelayed({ if (isAdded) loadHome(attempt + 1) }, 500)
            } else {
                providerLabel.text = "NO EXTENSION"
                showEmptyState()
                refresh.isRefreshing = false
            }
            return
        }
        providerLabel.text = "SOURCE  •  ${api.name.uppercase()}"
        if (rows.childCount == 0) showSkeleton()

        viewLifecycleOwner.lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    val page = api.mainPage.firstOrNull() ?: MainPageData("", "", false)
                    api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                }.getOrNull()
            }
            if (!isAdded) return@launch
            refresh.isRefreshing = false
            rows.removeAllViews()
            rowAnchors.clear()
            chipRow.removeAllViews()

            val lists = response?.items.orEmpty()
            if (lists.isEmpty()) {
                showEmptyState()
                return@launch
            }

            lists.forEachIndexed { index, list ->
                chipRow.addView(Ui.pill(context, list.name, index == 0) {
                    rowAnchors[list.name]?.let { target ->
                        (view as? SwipeRefreshLayout)?.let { sw ->
                            (sw.getChildAt(0) as? NestedScrollView)?.smoothScrollTo(0, target.top)
                        }
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = Ui.dp(context, 8)
                })
            }

            val heroItems = lists.first().list.take(7)
            if (heroItems.isNotEmpty()) {
                carousel.adapter = HeroAdapter(heroItems, { openDetail(it) }, { openDetail(it) })
                buildDots(heroItems.size)
                startAutoScroll(heroItems.size)
            }

            lists.forEach { list ->
                val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                block.addView(Ui.sectionHeader(context, list.name))
                val recycler = RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                    adapter = PosterAdapter(list.list, 124, 176) { openDetail(it) }
                    setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0)
                    clipToPadding = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setHasFixedSize(true)
                }
                block.addView(
                    recycler,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 236))
                )
                rows.addView(block)
                rowAnchors[list.name] = block
            }
        }
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
                val next = (carousel.currentItem + 1) % count
                carousel.setCurrentItem(next, true)
                handler.postDelayed(this, 6500)
            }
        }
        autoScroll = task
        handler.postDelayed(task, 6500)
    }

    private fun stopAutoScroll() {
        autoScroll?.let { handler.removeCallbacks(it) }
        autoScroll = null
    }

    private fun showEmptyState() {
        val context = context ?: return
        rows.removeAllViews()
        chipRow.removeAllViews()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 28), Ui.dp(context, 48), Ui.dp(context, 28), Ui.dp(context, 24))
        }
        box.addView(Ui.display(context, "Nothing here yet", 22f))
        box.addView(
            Ui.body(context, "Add an extension repository or import a .cs3 file, then pick a source with the button below."),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(context, 8)
            }
        )
        box.addView(Ui.spacer(context, 18))
        box.addView(Ui.filledButton(context, "Open extensions") {
            startActivity(Intent(context, ExtensionsActivity::class.java))
        })
        rows.addView(box)
    }

    override fun onResume() {
        super.onResume()
        if (!this::providerLabel.isInitialized) return
        val name = Providers.active()?.name ?: ""
        if (name.isBlank() || !providerLabel.text.toString().contains(name.uppercase())) {
            loadHome()
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
