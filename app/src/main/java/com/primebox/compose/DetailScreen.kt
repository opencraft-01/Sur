package com.primebox.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
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

@Composable
fun DetailScreen(
    url: String,
    title: String,
    poster: String?,
    apiName: String?,
    onBack: () -> Unit,
    onPlay: (ExtractorLink, String) -> Unit
) {
    val context = LocalContext.current
    val provider: MainAPI? = remember(apiName) {
        Providers.all().firstOrNull { it.name == apiName } ?: Providers.active()
    }

    var response by remember { mutableStateOf<LoadResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Loading…") }
    var links by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(url) {
        detailCache[url]?.let { cached ->
            response = cached
            loading = false
            status = listOfNotNull(cached.year?.toString(), cached.type.name).joinToString("   •   ")
            return@LaunchedEffect
        }
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
        status = listOfNotNull(loaded.year?.toString(), loaded.type.name).joinToString("   •   ")
    }

    fun fetchLinks(data: String, linkTitle: String) {
        linkCache[data]?.let { cached ->
            links = cached
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (!poster.isNullOrBlank()) {
                AsyncImage(
                    model = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ShimmerBox(Modifier.fillMaxSize(), corner = 0)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xF20A0A0D)))
            )
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clickable { onBack() }
                    .padding(16.dp)
                    .size(22.dp)
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

            val loaded = response
            if (loaded != null) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(
                        loaded.year?.toString(),
                        loaded.type.name,
                        loaded.duration?.takeIf { it > 0 }?.let { "$it min" }
                    ).forEach { Badge(it) }
                }
                loaded.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp).width(28.dp).height(28.dp)
                )
            }

            when (val res = loaded) {
                is MovieLoadResponse -> {
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { fetchLinks(res.dataUrl, res.name) }) { Text("▶  Play") }
                        OutlinedButton(onClick = { fetchLinks(res.dataUrl, res.name) }) { Text("Sources") }
                    }
                }

                is TvSeriesLoadResponse -> EpisodeList(res.episodes, res.name) { episode, name ->
                    fetchLinks(episode.data, name)
                }

                is AnimeLoadResponse -> EpisodeList(res.episodes.values.flatten(), res.name) { episode, name ->
                    fetchLinks(episode.data, name)
                }

                else -> Unit
            }

            if (linksLoading) {
                Text(
                    "Finding sources…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            if (links.isNotEmpty()) {
                SectionHeader("Sources", "${links.size}")
                links.forEach { link ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(link.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${link.type.name}   •   ${link.quality}p",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            androidx.compose.material3.Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    download(context, link, title, poster)
                                }.padding(8.dp).size(20.dp)
                            )
                            androidx.compose.material3.Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    Store.addHistory(
                                        Store.HistoryEntry(
                                            title = title,
                                            poster = poster,
                                            provider = provider?.name ?: "",
                                            url = url,
                                            time = System.currentTimeMillis()
                                        )
                                    )
                                    onPlay(link, title)
                                }.padding(8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<Episode>,
    title: String,
    onEpisode: (Episode, String) -> Unit
) {
    if (episodes.isEmpty()) return
    SectionHeader("Episodes", "${episodes.size}")
    episodes.take(300).forEachIndexed { index, episode ->
        val label = episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode ?: (index + 1)}"
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable { onEpisode(episode, "$title — $label") }
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(
                        "${episode.episode ?: (index + 1)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    episode.runTime?.let {
                        Text("$it min", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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

