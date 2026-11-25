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
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.primebox.data.Store
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var rows: LinearLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var carousel: ViewPager2
    private lateinit var heroContainer: FrameLayout
    private lateinit var providerLabel: TextView
    private val rowAnchors = LinkedHashMap<String, View>()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val scroll = NestedScrollView(context).apply {
            setBackgroundColor(Ui.bg(context))
            isFillViewport = true
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 32))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 8), 0)
        }
        val logo = ImageView(context).apply {
            setImageResource(com.primebox.R.drawable.ic_logo)
        }
        header.addView(logo, LinearLayout.LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26)))
        val brand = Ui.textView(context, "PrimeBox", 18f, Ui.text(context), bold = true)
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = Ui.dp(context, 8)
        })
        val extButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Ui.text(context))
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
            setOnClickListener { startActivity(Intent(context, ExtensionsActivity::class.java)) }
        }
        header.addView(extButton, LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)))
        val settingsButton = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Ui.text(context))
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
            setOnClickListener { startActivity(Intent(context, SettingsActivity::class.java)) }
        }
        header.addView(settingsButton, LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)))
        column.addView(header)

        // ---- search bar
        val search = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 14).toFloat()
                setColor(Ui.surface(context))
                setStroke(Ui.dp(context, 1), Ui.stroke(context))
            }
            setOnClickListener { startActivity(Intent(context, SearchActivity::class.java)) }
        }
        val glass = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Ui.textDim(context))
        }
        search.addView(glass, LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)))
        search.addView(
            Ui.textView(context, "Search movies, series, anime…", 14f, Ui.textDim(context)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(context, 10) }
        )
        search.addView(Ui.textView(context, "Search", 13f, Ui.accent(context), bold = true))
        column.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), 0)
        })

        // ---- provider label
        providerLabel = Ui.textView(context, "", 11f, Ui.textDim(context))
        column.addView(providerLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(Ui.dp(context, 16), Ui.dp(context, 10), 0, 0)
        })

        // ---- category chips
        chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 16), Ui.dp(context, 8), Ui.dp(context, 16), Ui.dp(context, 4))
        }
        val chipScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }
        column.addView(chipScroll)

        // ---- hero carousel
        heroContainer = FrameLayout(context).apply {
            setBackgroundColor(Ui.surface(context))
        }
        carousel = ViewPager2(context).apply {
            adapter = HeroAdapter(emptyList()) { }
            offscreenPageLimit = 1
        }
        heroContainer.addView(carousel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(heroContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 260)).apply {
            setMargins(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 10))
        })

        // ---- rows
        rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(rows)

        loadHome()
        return scroll
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

    private fun loadHome() {
        val context = context ?: return
        val api: MainAPI? = Providers.active()
        if (api == null) {
            providerLabel.text = "No extension installed"
            showEmptyState()
            return
        }
        providerLabel.text = "Source: ${api.name}"
        viewLifecycleOwner.lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    val page = api.mainPage.firstOrNull() ?: MainPageData("", "", false)
                    api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                }.getOrNull()
            }
            if (!isAdded) return@launch
            rows.removeAllViews()
            rowAnchors.clear()
            chipRow.removeAllViews()

            val lists = response?.items.orEmpty()
            if (lists.isEmpty()) {
                showEmptyState()
                return@launch
            }

            lists.forEach { list ->
                chipRow.addView(Ui.pill(context, list.name, rowAnchors.isEmpty()) {
                    rowAnchors[list.name]?.let { target ->
                        (view as? NestedScrollView)?.smoothScrollTo(0, target.top)
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = Ui.dp(context, 8)
                })
            }

            val heroItems = lists.first().list.take(6)
            if (heroItems.isNotEmpty()) {
                carousel.adapter = HeroAdapter(heroItems) { openDetail(it) }
            }

            lists.forEach { list ->
                val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val titleRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 16), Ui.dp(context, 8))
                }
                titleRow.addView(
                    Ui.textView(context, list.name, 16f, Ui.text(context), bold = true),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                block.addView(titleRow)

                val recycler = RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                    adapter = PosterAdapter(list.list, 120, 170) { openDetail(it) }
                    setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 4), 0)
                    clipToPadding = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }
                block.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 210)))
                rows.addView(block)
                rowAnchors[list.name] = block
            }
        }
    }

    private fun showEmptyState() {
        val context = context ?: return
        rows.removeAllViews()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 24), Ui.dp(context, 40), Ui.dp(context, 24), Ui.dp(context, 24))
        }
        box.addView(Ui.textView(context, "Nothing to show yet", 17f, Ui.text(context), bold = true))
        box.addView(Ui.textView(context, "Add a repository or import a .cs3 extension, then pick one with the button below.", 13f, Ui.textDim(context)))
        box.addView(Ui.spacer(context, 16))
        box.addView(Ui.pill(context, "Open extensions", true) {
            startActivity(Intent(context, ExtensionsActivity::class.java))
        })
        rows.addView(box)
    }

    override fun onResume() {
        super.onResume()
        val name = Providers.active()?.name ?: ""
        if (providerLabel.text.toString() != "Source: $name") {
            providerLabel.text = if (name.isBlank()) "No extension installed" else "Source: $name"
            loadHome()
        }
    }
}
