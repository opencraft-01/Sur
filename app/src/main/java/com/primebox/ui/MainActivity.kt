package com.primebox.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.primebox.data.Store
import com.primebox.ext.ExtensionManager
import com.primebox.ext.Providers

class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var navBar: NavBar
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.bg(this))

        content = FrameLayout(this).apply { id = View.generateViewId() }

        navBar = NavBar(this) { index -> selectTab(index) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(navBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 64)))
        }
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Ui.accent(this@MainActivity))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#04120C"))
            contentDescription = "Switch extension"
            setOnClickListener { showExtensionSwitcher() }
        }
        root.addView(
            fab,
            FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 84))
            }
        )

        setContentView(root)

        selectTab(0, animate = false)
    }

    private fun selectTab(index: Int, animate: Boolean = true) {
        if (index == currentTab && animate) return
        currentTab = index
        navBar.select(index, animate)
        val fragment: Fragment = if (index == 0) HomeFragment() else LibraryFragment()
        supportFragmentManager.beginTransaction()
            .replace(content.id, fragment)
            .commit()
    }

    private fun showExtensionSwitcher() {
        val sheet = BottomSheetDialog(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 24))
            setBackgroundColor(Ui.surface(this@MainActivity))
        }
        list.addView(Ui.textView(this, "Extensions", 18f, Ui.text(this), bold = true))
        list.addView(Ui.textView(this, "Pick the source used on the home screen", 12f, Ui.textDim(this)))
        list.addView(Ui.spacer(this, 12))

        val providers = Providers.all()
        if (providers.isEmpty()) {
            list.addView(
                Ui.textView(
                    this,
                    "No extension installed yet. Open Extensions to add a repository or import a .cs3 file.",
                    13f,
                    Ui.textDim(this)
                )
            )
        } else {
            val active = Store.activeProvider
            providers.forEach { api ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 12))
                    background = GradientDrawable().apply {
                        cornerRadius = Ui.dp(this@MainActivity, 12).toFloat()
                        setColor(if (api.name == active) Ui.surfaceHigh(this@MainActivity) else Color.TRANSPARENT)
                        setStroke(Ui.dp(this@MainActivity, 1), Ui.stroke(this@MainActivity))
                    }
                    isClickable = true
                    setOnClickListener {
                        Providers.select(api)
                        sheet.dismiss()
                        selectTab(0, animate = false)
                        recreate()
                    }
                }
                row.addView(Ui.textView(this, api.name, 15f, Ui.text(this), bold = true))
                row.addView(Ui.textView(this, api.lang.uppercase(), 11f, Ui.textDim(this)))
                list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, Ui.dp(this@MainActivity, 8))
                })
            }
        }

        list.addView(Ui.spacer(this, 8))
        list.addView(Ui.pill(this, "Manage extensions", false) {
            sheet.dismiss()
            startActivity(android.content.Intent(this, ExtensionsActivity::class.java))
        })

        sheet.setContentView(list)
        sheet.show()
    }
}

/** Bottom bar with a sliding accent indicator. */
class NavBar(context: android.content.Context, private val onSelect: (Int) -> Unit) : FrameLayout(context) {

    private val indicator: View
    private val items = mutableListOf<LinearLayout>()
    private val labels = mutableListOf<TextView>()
    private val icons = mutableListOf<ImageView>()
    private var selected = 0

    init {
        setBackgroundColor(Ui.surface(context))
        indicator = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 18).toFloat()
                setColor(Ui.accent(context))
            }
        }
        addView(indicator, LayoutParams(0, Ui.dp(context, 36)).apply { gravity = Gravity.CENTER_VERTICAL })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        listOf("Home" to android.R.drawable.ic_menu_view, "Library" to android.R.drawable.ic_menu_sort_by_size)
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
                item.addView(image, LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)))
                item.addView(text)
                row.addView(item, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                items.add(item)
                labels.add(text)
                icons.add(image)
            }
    }

    fun select(index: Int, animate: Boolean) {
        selected = index
        val itemWidth = width / items.size
        val targetX = itemWidth * index + (itemWidth - Ui.dp(context, 96)) / 2f
        indicator.layoutParams = (indicator.layoutParams as LayoutParams).apply {
            width = Ui.dp(context, 96)
        }
        indicator.requestLayout()
        indicator.alpha = 0.18f
        if (animate) {
            indicator.animate().x(targetX).setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else {
            post { indicator.x = targetX }
        }
        labels.forEachIndexed { i, text ->
            text.setTextColor(if (i == index) Ui.accent(context) else Ui.textDim(context))
            icons[i].setColorFilter(if (i == index) Ui.accent(context) else Ui.textDim(context))
        }
    }
}
