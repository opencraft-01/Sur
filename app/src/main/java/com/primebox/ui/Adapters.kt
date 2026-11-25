package com.primebox.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.primebox.data.Store
import com.primebox.img.ImageLoader

/** Poster card used by the home rows and grids. */
class PosterAdapter(
    private var items: List<SearchResponse>,
    private val cardWidthDp: Int,
    private val cardHeightDp: Int,
    private val onClick: (SearchResponse) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    fun submit(list: List<SearchResponse>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val image: ImageView = ImageView(root.context)
        val title: TextView = Ui.textView(root.context, "", 12.5f, Ui.text(root.context), maxLines = 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(Ui.dp(context, cardWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, Ui.dp(context, 10), 0)
        }
        val holder = VH(column)
        val frame = FrameLayout(context)
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(holder.image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = Ui.dp(context, 10).toFloat()
            setColor(Ui.surfaceHigh(context))
        }
        frame.clipToOutline = true
        column.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, cardHeightDp)))
        column.addView(holder.title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 6)
        })
        column.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        ImageLoader.load(item.posterUrl, holder.image, item.posterHeaders, targetPx = 400)
    }

    override fun getItemCount(): Int = items.size
}

/** Wide hero card shown inside the ViewPager2 carousel. */
class HeroAdapter(
    private val items: List<SearchResponse>,
    private val onClick: (SearchResponse) -> Unit
) : RecyclerView.Adapter<HeroAdapter.VH>() {

    inner class VH(val root: FrameLayout) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { }
        }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val fade = View(context).apply { background = Ui.bottomFade() }
        frame.addView(fade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), Ui.dp(context, 14))
        }
        val title = Ui.textView(context, "", 22f, Color.WHITE, bold = true, maxLines = 1)
        val meta = Ui.textView(context, "", 12f, Color.parseColor("#D0D0D8"))
        info.addView(title)
        info.addView(meta)
        frame.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        frame.tag = image
        val holder = VH(frame)
        frame.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val image = holder.root.tag as ImageView
        ImageLoader.load(item.posterUrl, image, item.posterHeaders, targetPx = 900)
        val info = holder.root.getChildAt(2) as LinearLayout
        (info.getChildAt(0) as TextView).text = item.name
        val parts = mutableListOf<String>()
        item.type?.let { parts.add(it.name) }
        item.quality?.let { parts.add(it.name) }
        (info.getChildAt(1) as TextView).text = parts.joinToString("  •  ")
    }

    override fun getItemCount(): Int = items.size
}

/** Episode row for series. */
class EpisodeAdapter(
    private val items: List<Episode>,
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val title: TextView = Ui.textView(root.context, "", 14f, Ui.text(root.context), bold = true)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 12).toFloat()
                setColor(Ui.surface(context))
            }
        }
        val holder = VH(column)
        column.addView(holder.title)
        column.addView(holder.subtitle)
        column.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        column.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val episode = items[position]
        val label = episode.name?.takeIf { it.isNotBlank() }
            ?: "Episode ${episode.episode ?: (position + 1)}"
        holder.title.text = label
        val bits = mutableListOf<String>()
        episode.season?.let { bits.add("Season $it") }
        episode.runTime?.let { bits.add("${it} min") }
        holder.subtitle.text = bits.joinToString("  •  ")
    }

    override fun getItemCount(): Int = items.size
}

/** One playable stream link. */
class LinkAdapter(
    private val items: List<ExtractorLink>,
    private val onClick: (ExtractorLink) -> Unit
) : RecyclerView.Adapter<LinkAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val title: TextView = Ui.textView(root.context, "", 14f, Ui.text(root.context), bold = true)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 12).toFloat()
                setColor(Ui.surface(context))
            }
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(column)
        column.addView(holder.title)
        column.addView(holder.subtitle)
        column.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val link = items[position]
        holder.title.text = link.name
        holder.subtitle.text = "${link.type.name}  •  ${link.quality}p"
    }

    override fun getItemCount(): Int = items.size
}

/** History entry in the library tab. */
class HistoryAdapter(
    private var items: List<Store.HistoryEntry>,
    private val onClick: (Store.HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    fun submit(list: List<Store.HistoryEntry>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val image: ImageView = ImageView(root.context)
        val title: TextView = Ui.textView(root.context, "", 13f, Ui.text(root.context), bold = true, maxLines = 2)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context), maxLines = 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 12).toFloat()
                setColor(Ui.surface(context))
            }
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        val frame = FrameLayout(context)
        frame.addView(holder.image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.clipToOutline = true
        row.addView(frame, LinearLayout.LayoutParams(Ui.dp(context, 54), Ui.dp(context, 76)))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 10), 0, 0, 0)
        }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        row.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.provider
        ImageLoader.load(item.poster, holder.image, targetPx = 220)
    }

    override fun getItemCount(): Int = items.size
}
