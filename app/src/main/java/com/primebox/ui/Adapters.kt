package com.primebox.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.primebox.data.Downloader
import com.primebox.data.Store
import com.primebox.img.ImageLoader

/** Poster card for the home rows. */
class PosterAdapter(
    private var items: List<SearchResponse>,
    private val cardWidthDp: Int,
    private val cardHeightDp: Int,
    private val showTitles: Boolean = true,
    private val onClick: (SearchResponse) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    fun submit(list: List<SearchResponse>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val image: ImageView = ImageView(root.context)
        val title: TextView = Ui.textView(root.context, "", 12.5f, Ui.text(root.context), bold = true, maxLines = 2)
        val badge: TextView = Ui.badge(root.context, "")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(Ui.dp(context, cardWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, Ui.dp(context, 12), 0)
        }
        val holder = VH(column)

        val frame = FrameLayout(context).apply {
            background = Ui.shape(context, 18, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(
            holder.image,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val fade = View(context).apply { background = Ui.bottomFade() }
        frame.addView(
            fade,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 64), Gravity.BOTTOM)
        )
        frame.addView(
            holder.badge,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = Ui.dp(context, 8)
                bottomMargin = Ui.dp(context, 8)
            }
        )
        column.addView(
            frame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, cardHeightDp))
        )
        if (showTitles) {
            column.addView(
                holder.title,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = Ui.dp(context, 8)
                }
            )
        }
        column.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.title.visibility = if (showTitles) View.VISIBLE else View.GONE
        val quality = item.quality?.name
        if (quality.isNullOrBlank()) {
            holder.badge.visibility = View.GONE
        } else {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = quality
            holder.badge.setTextColor(Color.WHITE)
            holder.badge.background = Ui.shape(holder.root.context, 8, Ui.withAlpha(Color.BLACK, 0.55f))
        }
        ImageLoader.load(item.posterUrl, holder.image, item.posterHeaders, targetPx = 420)
    }

    override fun getItemCount(): Int = items.size
}

/** Large hero page inside the carousel. */
class HeroAdapter(
    private val items: List<SearchResponse>,
    private val onClick: (SearchResponse) -> Unit,
    private val onPlay: (SearchResponse) -> Unit
) : RecyclerView.Adapter<HeroAdapter.VH>() {

    inner class VH(val root: FrameLayout, val image: ImageView, val title: TextView, val meta: TextView, val plot: TextView, val badge: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            background = Ui.shape(context, 24, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(View(context).apply { background = Ui.bottomFade() }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(View(context).apply { background = Ui.topFade() }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 90), Gravity.TOP))

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), Ui.dp(context, 18))
        }
        val badge = Ui.badge(context, "", Color.WHITE, Ui.withAlpha(Color.BLACK, 0.45f))
        val badgeWrap = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        badgeWrap.addView(badge)
        info.addView(badgeWrap)
        val title = Ui.display(context, "", 24f).apply { setTextColor(Color.WHITE) }
        info.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 8)
        })
        val meta = Ui.textView(context, "", 12f, Color.parseColor("#C9CDD6"))
        info.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 4)
        })
        val plot = Ui.textView(context, "", 12.5f, Color.parseColor("#DDE1E8"), maxLines = 2)
        info.addView(plot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 6)
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val play = TextView(context).apply {
            text = "▶  Play"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(Color.parseColor("#04120C"))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(Ui.dp(context, 20), Ui.dp(context, 11), Ui.dp(context, 20), Ui.dp(context, 11))
            background = Ui.ripple(context, Ui.shape(context, 100, Ui.accent(context)))
            isClickable = true
        }
        val more = TextView(context).apply {
            text = "Details"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(Ui.dp(context, 18), Ui.dp(context, 11), Ui.dp(context, 18), Ui.dp(context, 11))
            background = Ui.ripple(context, Ui.shape(context, 100, Ui.withAlpha(Color.WHITE, 0.16f)))
            isClickable = true
        }
        actions.addView(play)
        actions.addView(Ui.horizontalSpace(context, 10))
        actions.addView(more)
        info.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 12)
        })
        frame.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        val holder = VH(frame, image, title, meta, plot, badge)
        play.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onPlay(items[position])
        }
        frame.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        more.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        ImageLoader.load(item.posterUrl, holder.image, item.posterHeaders, targetPx = 1000)
        holder.title.text = item.name
        val bits = mutableListOf<String>()
        item.type?.let { bits.add(it.name) }
        item.quality?.let { bits.add(it.name) }
        holder.meta.text = bits.joinToString("   •   ")
        holder.badge.text = item.type?.name ?: "TRENDING"
        holder.plot.text = ""
        holder.plot.visibility = View.GONE
    }

    override fun getItemCount(): Int = items.size
}

/** Episode row. */
class EpisodeAdapter(
    private val items: List<Episode>,
    private val onClick: (Episode, Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val index: TextView = Ui.textView(root.context, "", 13f, Ui.accent(root.context), bold = true)
        val title: TextView = Ui.textView(root.context, "", 14f, Ui.text(root.context), bold = true, maxLines = 1)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12))
            background = Ui.ripple(context, Ui.shape(context, 16, Ui.surface(context)))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val indexWrap = FrameLayout(context).apply {
            background = Ui.shape(context, 100, Ui.accentSoft(context))
        }
        indexWrap.addView(holder.index, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        indexWrap.setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
        row.addView(indexWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = Ui.dp(context, 12)
        })
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position], position)
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val episode = items[position]
        holder.index.text = (episode.episode ?: (position + 1)).toString()
        holder.title.text = episode.name?.takeIf { it.isNotBlank() }
            ?: "Episode ${episode.episode ?: (position + 1)}"
        val bits = mutableListOf<String>()
        episode.season?.let { bits.add("Season $it") }
        episode.runTime?.let { bits.add("$it min") }
        holder.subtitle.text = if (bits.isEmpty()) "Tap to load sources" else bits.joinToString("   •   ")
    }

    override fun getItemCount(): Int = items.size
}

/** Playable source with a download action. */
class LinkAdapter(
    private val items: List<ExtractorLink>,
    private val onClick: (ExtractorLink) -> Unit,
    private val onDownload: (ExtractorLink) -> Unit
) : RecyclerView.Adapter<LinkAdapter.VH>() {

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val title: TextView = Ui.textView(root.context, "", 14f, Ui.text(root.context), bold = true, maxLines = 1)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12))
            background = Ui.ripple(context, Ui.shape(context, 16, Ui.surface(context)))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val download = Ui.iconButton(context, android.R.drawable.stat_sys_download, Ui.textDim(context)) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onDownload(items[position])
        }
        row.addView(download)
        val play = Ui.iconButton(context, android.R.drawable.ic_media_play, Ui.accent(context)) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        row.addView(play)
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val link = items[position]
        holder.title.text = link.name.ifBlank { "Stream" }
        val bits = mutableListOf<String>()
        bits.add(
            when (link.type) {
                ExtractorLinkType.M3U8 -> "HLS"
                ExtractorLinkType.DASH -> "DASH"
                ExtractorLinkType.TORRENT -> "Torrent"
                ExtractorLinkType.MAGNET -> "Magnet"
                else -> "MP4"
            }
        )
        if (link.quality > 0) bits.add("${link.quality}p")
        holder.subtitle.text = bits.joinToString("   •   ")
    }

    override fun getItemCount(): Int = items.size
}

/** Watch-history entry. */
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
        val title: TextView = Ui.textView(root.context, "", 13.5f, Ui.text(root.context), bold = true, maxLines = 2)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context), maxLines = 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10))
            background = Ui.ripple(context, Ui.shape(context, 16, Ui.surface(context)))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val frame = FrameLayout(context).apply {
            background = Ui.shape(context, 12, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(holder.image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(frame, LinearLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 74)))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), 0, 0, 0)
        }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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

/** Downloaded / downloading item. */
class DownloadAdapter(
    private var items: List<Downloader.Item>,
    private val onClick: (Downloader.Item) -> Unit,
    private val onDelete: (Downloader.Item) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    fun submit(list: List<Downloader.Item>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        val image: ImageView = ImageView(root.context)
        val title: TextView = Ui.textView(root.context, "", 13.5f, Ui.text(root.context), bold = true, maxLines = 1)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context), maxLines = 1)
        val progress: ProgressBar = ProgressBar(root.context, null, android.R.attr.progressBarStyleHorizontal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10))
            background = Ui.ripple(context, Ui.shape(context, 16, Ui.surface(context)))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val frame = FrameLayout(context).apply {
            background = Ui.shape(context, 12, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(holder.image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(frame, LinearLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 74)))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0)
        }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        holder.progress.max = 100
        holder.progress.progressTintList = android.content.res.ColorStateList.valueOf(Ui.accent(context))
        column.addView(holder.progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 18)).apply {
            topMargin = Ui.dp(context, 6)
        })
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Ui.iconButton(context, android.R.drawable.ic_menu_delete, Ui.textDim(context)) {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onDelete(items[position])
        })
        row.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = when (item.state) {
            Downloader.State.DONE -> "Downloaded  •  ${item.quality}p"
            Downloader.State.RUNNING -> "Downloading… ${item.progress}%"
            Downloader.State.FAILED -> "Failed — tap to retry"
            else -> "Queued"
        }
        holder.progress.visibility = if (item.state == Downloader.State.RUNNING) View.VISIBLE else View.GONE
        holder.progress.progress = item.progress
        ImageLoader.load(item.poster, holder.image, targetPx = 220)
    }

    override fun getItemCount(): Int = items.size
}
