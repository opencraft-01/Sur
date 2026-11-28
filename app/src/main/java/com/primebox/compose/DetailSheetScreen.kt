@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.primebox.compose

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.primebox.data.Card
import com.primebox.data.Downloader
import com.primebox.data.Store
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** In-memory caches so reopening a title or a source list is instant. */
private val detailCache = HashMap<String, LoadResponse>()
private val linkCache = HashMap<String, List<ExtractorLink>>()

/**
 * Details sheet, ported from the CineGlass reference: full-bleed backdrop,
 * then a curved sheet that rides over it with the title, meta chips, the
 * primary "Watch Now" action, the story line and the episode list.
 */
@Composable
fun DetailSheetScreen(
    url: String,
    title: String,
    poster: String?,
    apiName: String?,
    quality: String?,
    ctx: SharedCtx? = null,
    onBack: () -> Unit,
    onPlay: (ExtractorLink, String) -> Unit
) {
    val c = Cine.c
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val provider: MainAPI? = remember(apiName) {
        Providers.all().firstOrNull { it.name == apiName } ?: Providers.active()
    }

    var response by remember { mutableStateOf<LoadResponse?>(detailCache[url]) }
    var loading by remember { mutableStateOf(response == null) }
    var status by remember { mutableStateOf("") }
    var links by remember { mutableStateOf(linkCache.values.firstOrNull { false }.orEmpty()) }
    var linksLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    fun fetchLinks(data: String) {
        linkCache[data]?.let {
            links = it
            return
        }
        val api = provider ?: return
        linksLoading = true
        scope.launch {
            val collected = withContext(Dispatchers.IO) {
                val out = ArrayList<ExtractorLink>()
                repeat(2) { attempt ->
                    if (out.isEmpty()) {
                        runCatching {
                            withTimeout(25_000) {
                                api.loadLinks(
                                    data = data,
                                    isCasting = false,
                                    subtitleCallback = { _: SubtitleFile -> },
                                    callback = { link -> out.add(link) }
                                )
                            }
                        }
                        if (out.isEmpty() && attempt == 0) Thread.sleep(400)
                    }
                }
                out.sortedByDescending { it.quality }
            }
            links = collected
            if (collected.isNotEmpty()) linkCache[data] = collected
            linksLoading = false
            if (collected.isEmpty()) status = "No playable source found"
        }
    }

    LaunchedEffect(url) {
        val cached = detailCache[url]
        if (cached != null) {
            response = cached
            loading = false
            return@LaunchedEffect
        }
        // let the open animation finish first: a network call in the first frames
        // is what made the morph stutter
        kotlinx.coroutines.delay(80)
        val api = provider ?: run { loading = false; status = "No extension"; return@LaunchedEffect }
        val result = withContext(Dispatchers.IO) {
            runCatching { withTimeout(20_000) { api.load(url) } }
        }
        loading = false
        val loaded = result.getOrNull()
        if (loaded == null) {
            status = result.exceptionOrNull()?.message ?: "Could not load"
            return@LaunchedEffect
        }
        detailCache[url] = loaded
        response = loaded
        // prefetch sources for a movie so Watch Now is instant
        if (loaded is MovieLoadResponse) fetchLinks(loaded.dataUrl)
    }

    val loaded = response
    val movieCard = Card(title, url, poster, loaded?.type?.name, quality, apiName)
    val accent = c.accent

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- backdrop ----
        Box(Modifier.fillMaxWidth().height(420.dp)) {
            PosterArt(movieCard, Modifier.fillMaxSize().sharedPoster(ctx, "poster-$url"), showTitle = true, titleSize = 40.sp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x66000000),
                        0.3f to Color.Transparent,
                        1f to Color(0x99000000)
                    )
                )
            )
            GlassIconButton(
                Icons.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
                tint = Color.White,
                overArt = true
            )
        }

        // ---- curved sheet ----
        Column(
            Modifier
                .offset(y = (-32).dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.sheet)
        ) {
            Box {
                // ambient glow from the accent colour
                if (c.isDark) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp).background(
                            Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))
                        )
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(2.dp).background(
                        Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent))
                    )
                )

                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp)).background(c.textMuted.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                loaded?.name ?: title,
                                color = c.text,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            QualityBadge(quality ?: "HD")
                        }
                        Text(
                            listOfNotNull(
                                loaded?.year?.toString(),
                                loaded?.type?.name,
                                loaded?.duration?.takeIf { it > 0 }?.let { "$it min" }
                            ).joinToString("   •   ").ifBlank { status },
                            color = c.textMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.glassBorder))
                        Spacer(Modifier.height(14.dp))

                        // meta chips + actions
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            loaded?.year?.let { GlassChip(it.toString()) }
                            Spacer(Modifier.width(8.dp))
                            GlassChip(loaded?.type?.name ?: "Movie")
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(
                                Icons.Filled.Share,
                                onClick = {
                                    val i = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Watch ${loaded?.name ?: title}")
                                    }
                                    context.startActivity(Intent.createChooser(i, "Share"))
                                },
                                size = 38.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            GlassIconButton(
                                Icons.Filled.Download,
                                onClick = {
                                    links.firstOrNull()?.let { download(context, it, title, poster) }
                                        ?: status.let { fetchLinks((loaded as? MovieLoadResponse)?.dataUrl ?: url) }
                                },
                                size = 38.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            GlassIconButton(
                                Icons.Filled.Refresh,
                                onClick = { links = emptyList(); linkCache.remove((loaded as? MovieLoadResponse)?.dataUrl ?: url); fetchLinks((loaded as? MovieLoadResponse)?.dataUrl ?: url) },
                                size = 38.dp
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        GlowButton(
                            if (linksLoading) "Finding sources…" else "Watch Now",
                            onClick = {
                                val first = links.firstOrNull()
                                if (first != null) {
                                    Store.addHistory(
                                        Store.HistoryEntry(
                                            title = loaded?.name ?: title,
                                            poster = poster,
                                            provider = provider?.name ?: "",
                                            url = url,
                                            time = System.currentTimeMillis()
                                        )
                                    )
                                    onPlay(first, loaded?.name ?: title)
                                } else {
                                    fetchLinks((loaded as? MovieLoadResponse)?.dataUrl ?: url)
                                }
                            },
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // story line
                        val plot = loaded?.plot
                        if (!plot.isNullOrBlank()) {
                            Spacer(Modifier.height(22.dp))
                            Text("Story Line", color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.animateContentSize()) {
                                Text(
                                    plot,
                                    color = c.textMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (expanded) "Less" else "...More",
                                    color = c.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // episodes
                    val episodes: List<Episode> = when (loaded) {
                        is TvSeriesLoadResponse -> loaded.episodes
                        is AnimeLoadResponse -> loaded.episodes.values.flatten()
                        else -> emptyList()
                    }
                    if (episodes.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Episodes", color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${episodes.size}", color = c.textMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(episodes.take(60)) { episode ->
                                val label = episode.name?.takeIf { it.isNotBlank() }
                                    ?: "Episode ${episode.episode ?: 0}"
                                Column(
                                    Modifier
                                        .width(150.dp)
                                        .glass(RoundedCornerShape(14.dp))
                                        .clickable {
                                            links = emptyList()
                                            fetchLinks(episode.data)
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        episode.runTime?.let { "$it min" } ?: "Tap to load sources",
                                        color = c.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }

                    // sources
                    if (links.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sources", color = c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.weight(1f))
                            Text("${links.size}", color = c.textMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.padding(horizontal = 22.dp)) {
                            links.forEach { link ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .glass(RoundedCornerShape(14.dp))
                                        .clickable {
                                            Store.addHistory(
                                                Store.HistoryEntry(
                                                    title = loaded?.name ?: title,
                                                    poster = poster,
                                                    provider = provider?.name ?: "",
                                                    url = url,
                                                    time = System.currentTimeMillis()
                                                )
                                            )
                                            onPlay(link, loaded?.name ?: title)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(link.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                        Text(
                                            "${link.type.name}   •   ${link.quality}p",
                                            color = c.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "Download",
                                        tint = c.textMuted,
                                        modifier = Modifier.clickable {
                                            download(context, link, loaded?.name ?: title, poster)
                                        }.padding(6.dp).size(18.dp)
                                    )
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = c.accent,
                                        modifier = Modifier.clickable { onPlay(link, loaded?.name ?: title) }
                                            .padding(6.dp).size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (status.isNotBlank()) {
                        Text(
                            status,
                            color = c.textMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

private fun download(
    context: android.content.Context,
    link: ExtractorLink,
    title: String,
    poster: String?
) {
    if (link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.MAGNET ||
        link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.TORRENT
    ) {
        android.widget.Toast.makeText(context, "Torrent downloads are not supported", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val item = Downloader.Item(
        id = Integer.toHexString(link.url.hashCode()),
        title = title,
        poster = poster,
        url = link.url,
        referer = link.referer,
        headers = link.headers,
        type = if (link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8) "M3U8" else "VIDEO",
        quality = link.quality,
        provider = ""
    )
    Downloader.start(context, item)
    android.widget.Toast.makeText(context, "Download started — see Library", android.widget.Toast.LENGTH_SHORT).show()
}
