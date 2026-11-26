package com.primebox.ui

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
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.primebox.data.Card
import com.primebox.data.Downloader
import com.primebox.data.Store
import com.primebox.img.ImageLoader

/**
 * CloudStream-style poster card: fixed 114x180 (or 200x114 for wide rows), rounded,
 * title overlaid at the bottom over a scrim, shimmer until the thumbnail arrives.
 */
class PosterAdapter(
    private var items: List<Card>,
    private val cardWidthDp: Int,
    private val cardHeightDp: Int,
    private val showTitles: Boolean = true,
    private val onClick: (Card) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    fun submit(list: List<Card>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        val image: ImageView = ImageView(root.context)
        val shimmer: View = skeletonFill(root.context, 14)
        val title: TextView = Ui.textView(root.context, "", 11.5f, Color.WHITE, bold = true, maxLines = 2)
        val badge: TextView = Ui.badge(root.context, "")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val card = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(Ui.dp(context, cardWidthDp), Ui.dp(context, cardHeightDp)).apply {
                rightMargin = Ui.dp(context, 10)
            }
            background = Ui.shape(context, 14, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        val holder = VH(card)

        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        card.addView(holder.image, FrameLayout.LayoutParams(-1, -1))
        card.addView(holder.shimmer, FrameLayout.LayoutParams(-1, -1))
        card.addView(
            View(context).apply { background = Ui.bottomFade() },
            FrameLayout.LayoutParams(-1, Ui.dp(context, if (showTitles) 72 else 48), Gravity.BOTTOM)
        )
        card.addView(
            holder.badge,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = Ui.dp(context, 6)
                rightMargin = Ui.dp(context, 6)
            }
        )
        if (showTitles) {
            card.addView(
                holder.title,
                FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                    leftMargin = Ui.dp(context, 7)
                    rightMargin = Ui.dp(context, 7)
                    bottomMargin = Ui.dp(context, 6)
                }
            )
        }
        card.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(items[position])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.title.visibility = if (showTitles) View.VISIBLE else View.GONE
        val quality = item.quality
        if (quality.isNullOrBlank()) {
            holder.badge.visibility = View.GONE
        } else {
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = quality
            holder.badge.setTextColor(Color.WHITE)
            holder.badge.background = Ui.shape(holder.root.context, 6, Ui.withAlpha(Color.BLACK, 0.55f))
        }
        ImageLoader.load(item.poster, holder.image, null, 400, holder.shimmer)
    }

    override fun getItemCount(): Int = items.size
}

/** Large hero page with shimmer while the artwork loads. */
class HeroAdapter(
    private val items: List<Card>,
    private val onClick: (Card) -> Unit,
    private val onPlay: (Card) -> Unit
) : RecyclerView.Adapter<HeroAdapter.VH>() {

    inner class VH(val root: FrameLayout, val image: ImageView, val title: TextView, val meta: TextView, val badge: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            background = Ui.shape(context, 24, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(skeletonFill(context, 24), FrameLayout.LayoutParams(-1, -1).apply { })
        frame.addView(View(context).apply { background = Ui.bottomFade() }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(
            View(context).apply { background = Ui.topFade() },
            FrameLayout.LayoutParams(-1, Ui.dp(context, 90), Gravity.TOP)
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), Ui.dp(context, 18))
        }
        val badge = Ui.badge(context, "", Color.WHITE, Ui.withAlpha(Color.BLACK, 0.45f))
        info.addView(badge)
        val title = Ui.display(context, "", 24f).apply { setTextColor(Color.WHITE) }
        info.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 8) })
        val meta = Ui.textView(context, "", 12f, Color.parseColor("#C9CDD6"))
        info.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 4) })

        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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
        info.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(context, 12) })
        frame.addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val holder = VH(frame, image, title, meta, badge)
        play.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onPlay(items[p])
        }
        frame.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        }
        more.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        ImageLoader.load(item.poster, holder.image, null, 1000)
        holder.title.text = item.name
        val bits = mutableListOf<String>()
        item.type?.let { bits.add(it) }
        item.quality?.let { bits.add(it) }
        holder.meta.text = bits.joinToString("   •   ")
        // Google TV style ranking chip: TOP 1, TOP 2, …
        holder.badge.text = if (position < 10) "TOP ${position + 1}" else (item.type ?: "TRENDING")
        holder.badge.background = Ui.shape(
            holder.root.context, 8,
            Ui.withAlpha(if (position < 3) Ui.accent(holder.root.context) else Color.BLACK, 0.62f)
        )
        holder.badge.setTextColor(if (position < 3) Color.parseColor("#04120C") else Color.WHITE)
    }

    override fun getItemCount(): Int = items.size

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
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
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val indexWrap = FrameLayout(context).apply { background = Ui.shape(context, 100, Ui.accentSoft(context)) }
        indexWrap.addView(holder.index, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        indexWrap.setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
        row.addView(indexWrap, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dp(context, 12) })
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p], p)
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val episode = items[position]
        holder.index.text = (episode.episode ?: (position + 1)).toString()
        holder.title.text = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode ?: (position + 1)}"
        val bits = mutableListOf<String>()
        episode.season?.let { bits.add("Season $it") }
        episode.runTime?.let { bits.add("$it min") }
        holder.subtitle.text = if (bits.isEmpty()) "Tap to load sources" else bits.joinToString("   •   ")
    }

    override fun getItemCount(): Int = items.size

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
}

/** Playable source with download action. */
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
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.iconButton(context, android.R.drawable.stat_sys_download, Ui.textDim(context)) {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onDownload(items[p])
        })
        row.addView(Ui.iconButton(context, android.R.drawable.ic_media_play, Ui.accent(context)) {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        })
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

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
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
        val shimmer: View = skeletonFill(root.context, 12)
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
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val frame = FrameLayout(context).apply {
            background = Ui.shape(context, 12, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(holder.image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(holder.shimmer, FrameLayout.LayoutParams(-1, -1))
        row.addView(frame, LinearLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 74)))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), 0, 0, 0)
        }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.provider
        ImageLoader.load(item.poster, holder.image, null, 220, holder.shimmer)
    }

    override fun getItemCount(): Int = items.size

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
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
        val shimmer: View = skeletonFill(root.context, 12)
        val title: TextView = Ui.textView(root.context, "", 13.5f, Ui.text(root.context), bold = true, maxLines = 1)
        val subtitle: TextView = Ui.textView(root.context, "", 11f, Ui.textDim(root.context), maxLines = 1)
        val progress: WavyProgressView = WavyProgressView(root.context)
        val stats: TextView = Ui.textView(root.context, "", 10.5f, Ui.textDim(root.context), maxLines = 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10))
            background = Ui.ripple(context, Ui.shape(context, 16, Ui.surface(context)))
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, Ui.dp(context, 8)) }
        }
        val holder = VH(row)
        val frame = FrameLayout(context).apply {
            background = Ui.shape(context, 12, Ui.surfaceHigh(context))
            clipToOutline = true
        }
        holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
        frame.addView(holder.image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(holder.shimmer, FrameLayout.LayoutParams(-1, -1))
        row.addView(frame, LinearLayout.LayoutParams(Ui.dp(context, 52), Ui.dp(context, 74)))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 8), 0)
        }
        column.addView(holder.title)
        column.addView(holder.subtitle)
        column.addView(holder.progress, LinearLayout.LayoutParams(-1, Ui.dp(context, 20)).apply { topMargin = Ui.dp(context, 6) })
        holder.stats.gravity = Gravity.CENTER_HORIZONTAL
        column.addView(holder.stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 2) })
        row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Ui.iconButton(context, android.R.drawable.ic_menu_delete, Ui.textDim(context)) {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onDelete(items[p])
        })
        row.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p])
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
        val running = item.state == Downloader.State.RUNNING
        holder.progress.visibility = if (running) View.VISIBLE else View.GONE
        holder.stats.visibility = if (running) View.VISIBLE else View.GONE
        if (running) {
            holder.progress.setProgress(item.progress / 100f)
            holder.stats.text = String.format(
                java.util.Locale.US,
                "%d%%   •   %s / %s   •   %s",
                item.progress,
                bytes(item.bytes),
                bytes(item.totalBytes),
                if (item.speed > 0) bytes(item.speed) + "/s" else "—"
            )
        }
        ImageLoader.load(item.poster, holder.image, null, 220, holder.shimmer)
    }

    override fun getItemCount(): Int = items.size

    private fun bytes(value: Long): String = when {
        value <= 0 -> "0 MB"
        value < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", value / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
    }
}
