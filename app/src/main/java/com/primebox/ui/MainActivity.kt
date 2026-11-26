package com.primebox.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.primebox.data.Store
import com.primebox.ext.Providers

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var navBar: NavBar
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.bg(this))

        content = FrameLayout(this).apply { id = View.generateViewId() }
        navBar = NavBar(this) { index -> selectTab(index) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                navBar,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val fab = FloatingActionButton(this).apply {
            setImageResource(com.primebox.R.drawable.ic_layers)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Ui.accent(this@MainActivity))
            imageTintList = android.content.res.ColorStateList.valueOf(Ui.onAccent(this@MainActivity))
            compatElevation = Ui.dp(this@MainActivity, 6).toFloat()
            contentDescription = "Switch extension"
            setOnClickListener { openSwitcher() }
        }
        root.addView(
            fab,
            FrameLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 96))
            }
        )

        setContentView(root)
        selectTab(0, animate = false)
    }

    override fun onResume() {
        super.onResume()
        // Give the extension library an Activity context so WebView based
        // link resolvers (Cloudflare, DDoS-Guard) work.
        runCatching { com.lagradost.api.setContext(this) }
    }

    private fun selectTab(index: Int, animate: Boolean = true) {
        if (index == currentTab && animate) return
        currentTab = index
        navBar.select(index, animate)
        val fragment: Fragment = if (index == 0) HomeFragment() else LibraryFragment()
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(content.id, fragment)
            .commit()
    }

    fun openSwitcher() {
        val sheet = BottomSheetDialog(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24))
            setBackgroundColor(Ui.surface(this@MainActivity))
        }

        val handle = View(this).apply { background = Ui.shape(this@MainActivity, 100, Ui.stroke(this@MainActivity)) }
        column.addView(
            handle,
            LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 36), Ui.dp(this@MainActivity, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = Ui.dp(this@MainActivity, 16)
            }
        )

        column.addView(Ui.headline(this, "Choose extension"))
        column.addView(Ui.body(this, "The home screen is built from the selected source"))
        column.addView(Ui.spacer(this, 14))

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(listBox) }
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 340)))

        val search = EditText(this).apply {
            hint = "Search extensions"
            setHintTextColor(Ui.textDim(this@MainActivity))
            setTextColor(Ui.text(this@MainActivity))
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = Ui.shape(this@MainActivity, 100, Ui.surface(this@MainActivity), 1, Ui.stroke(this@MainActivity))
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 10))
        }
        column.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun render(filter: String) {
            listBox.removeAllViews()
            val providers = Providers.all().filter { filter.isBlank() || it.name.contains(filter, true) }
            if (providers.isEmpty()) {
                listBox.addView(
                    Ui.body(this, "No extension found. Open Extensions to add a repository or import a .cs3 file."),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Ui.dp(this@MainActivity, 16)
                    }
                )
                return
            }
            val active = Store.activeProvider
            providers.forEach { api ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 13), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 13))
                    background = Ui.ripple(
                        this@MainActivity,
                        Ui.shape(
                            this@MainActivity, 16,
                            if (api.name == active) Ui.accentSoft(this@MainActivity) else Ui.surface(this@MainActivity),
                            1,
                            if (api.name == active) Ui.accent(this@MainActivity) else Ui.stroke(this@MainActivity)
                        )
                    )
                    isClickable = true
                    setOnClickListener {
                        Providers.select(api)
                        sheet.dismiss()
                        recreate()
                    }
                }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(Ui.title(this, api.name))
                val sub = mutableListOf<String>()
                if (api.lang.isNotBlank()) sub.add(api.lang.uppercase())
                if (api.mainUrl.isNotBlank() && api.mainUrl != "NONE") sub.add(api.mainUrl.removePrefix("https://").removePrefix("http://"))
                info.addView(Ui.textView(this, sub.joinToString("   •   "), 11f, Ui.textDim(this), maxLines = 1))
                row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (api.name == active) row.addView(Ui.badge(this, "ACTIVE", Ui.onAccent(this), Ui.accent(this)))
                listBox.addView(
                    row,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, Ui.dp(this@MainActivity, 8), 0, 0)
                    }
                )
            }
        }

        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })

        column.addView(Ui.spacer(this, 12))
        column.addView(Ui.filledButton(this, "Manage extensions") {
            sheet.dismiss()
            startActivity(Intent(this, ExtensionsActivity::class.java))
        })

        sheet.setContentView(column)
        sheet.show()
    }
}

/** Bottom navigation with a spring-animated pill indicator. */
class NavBar(context: android.content.Context, private val onSelect: (Int) -> Unit) : FrameLayout(context) {

    private val indicator: View
    private val labels = mutableListOf<TextView>()
    private val icons = mutableListOf<ImageView>()
    private var selected = -1

    init {
        setBackgroundColor(Ui.surface(context))
        setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 10))
        indicator = View(context).apply {
            background = Ui.shape(context, 100, Ui.withAlpha(Ui.accent(context), 0.16f))
            alpha = 0f
        }
        addView(indicator, LayoutParams(0, Ui.dp(context, 40)).apply { gravity = Gravity.CENTER_VERTICAL })

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        listOf("Home" to com.primebox.R.drawable.ic_home, "Library" to com.primebox.R.drawable.ic_library)
            .forEachIndexed { index, (label, icon) ->
                val item = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    setOnClickListener { onSelect(index) }
                }
                val image = ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(Ui.textDim(context))
                }
                val text = Ui.textView(context, label, 11f, Ui.textDim(context), bold = true)
                item.addView(image, LinearLayout.LayoutParams(Ui.dp(context, 22), Ui.dp(context, 22)))
                item.addView(text, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(context, 3)
                })
                row.addView(item, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                labels.add(text)
                icons.add(image)
            }
    }

    fun select(index: Int, animate: Boolean) {
        if (index == selected) return
        selected = index
        labels.forEachIndexed { i, text ->
            val active = i == index
            text.setTextColor(if (active) Ui.accent(context) else Ui.textDim(context))
            icons[i].setColorFilter(if (active) Ui.accent(context) else Ui.textDim(context))
            icons[i].animate()
                .scaleX(if (active) 1.12f else 1f)
                .scaleY(if (active) 1.12f else 1f)
                .setDuration(if (animate) 260 else 0)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }
        post {
            val itemWidth = width / labels.size
            val pillWidth = Ui.dp(context, 92)
            val target = (itemWidth * index + (itemWidth - pillWidth) / 2f)
            indicator.layoutParams = (indicator.layoutParams as LayoutParams).apply { this.width = pillWidth }
            indicator.alpha = 1f
            if (animate) {
                SpringAnimation(indicator, DynamicAnimation.X, target).apply {
                    spring = SpringForce(target).apply {
                        stiffness = SpringForce.STIFFNESS_LOW
                        dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                    }
                    start()
                }
            } else {
                indicator.x = target
            }
        }
    }
}
