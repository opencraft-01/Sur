@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)

package com.primebox.compose

import android.content.Intent
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.primebox.data.Downloader
import com.primebox.data.Net
import com.primebox.data.Store
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections

/** Small in-memory caches so reopening a title or a source list is instant (bounded LRU). */
private val detailCache = object : LinkedHashMap<String, LoadResponse>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LoadResponse>?): Boolean = size > 30
}
private val linkCache = object : LinkedHashMap<String, List<ExtractorLink>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ExtractorLink>>?): Boolean = size > 60
}

@Composable
fun DetailScreen(
    url: String,
    title: String,
    poster: String?,
    apiName: String?,
    sharedKey: String,
    ctx: SharedCtx? = null,
    onBack: () -> Unit,
    onPlay: (ExtractorLink, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val provider: MainAPI? = remember(apiName) {
        Providers.all().firstOrNull { it.name == apiName } ?: Providers.active()
    }

    var response by remember { mutableStateOf<LoadResponse?>(detailCache[url]) }
    var loading by remember { mutableStateOf(response == null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var sourcesFor by remember { mutableStateOf<String?>(null) } // the episode / movie the sheet is about
    var showSources by remember { mutableStateOf(false) }
    var links by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var linksLoading by remember { mutableStateOf(false) }
    var linksError by remember { mutableStateOf<String?>(null) }
    var autoPlay by remember { mutableStateOf(false) }
    var linksJob by remember { mutableStateOf<Job?>(null) }

    var expanded by remember { mutableStateOf(false) }
    var dub by remember { mutableStateOf<DubStatus?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }

    val loaded = response
    val displayTitle = loaded?.name ?: title

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun playLink(link: ExtractorLink) {
        Store.addHistory(
            Store.HistoryEntry(
                title = displayTitle,
                poster = poster,
                provider = provider?.name ?: "",
                url = url,
                time = System.currentTimeMillis()
            )
        )
        showSources = false
        onPlay(link, displayTitle)
    }

    fun fetchLinks(data: String, force: Boolean = false) {
        val api = provider ?: return
        linksJob?.cancel() // answers of an older request must never overwrite the newest one
        sourcesFor = data
        linksError = null
        if (!force) {
            linkCache[data]?.let {
                links = it
                linksLoading = false
                if (autoPlay && it.isNotEmpty()) {
                    autoPlay = false
                    playLink(it.first())
                }
                return
            }
        }
        links = emptyList()
        linksLoading = true
        linksJob = scope.launch {
            val collected = withContext(Dispatchers.IO) {
                // extensions report links from several coroutines at once
                val out = Collections.synchronizedList(ArrayList<ExtractorLink>())
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
                        ensureActive()
                        if (out.isEmpty() && attempt == 0) delay(400)
                    }
                }
                out.toList().sortedByDescending { it.quality }
            }
            links = collected
            linksLoading = false
            if (collected.isEmpty()) {
                linksError = "No playable source found"
                autoPlay = false
            } else {
                linkCache[data] = collected
                if (autoPlay) {
                    autoPlay = false
                    playLink(collected.first())
                }
            }
        }
    }

    LaunchedEffect(url, reloadTick) {
        if (reloadTick == 0) {
            detailCache[url]?.let {
                response = it
                loading = false
                return@LaunchedEffect
            }
        }
        val api = provider ?: run {
            loading = false
            loadError = "No extension is available for this title"
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        val result = withContext(Dispatchers.IO) { runCatching { withTimeout(20_000) { api.load(url) } } }
        loading = false
        val value = result.getOrNull()
        if (value == null) {
            loadError = result.exceptionOrNull()?.message ?: "Could not load this title"
            return@LaunchedEffect
        }
        detailCache[url] = value
        response = value
        // prefetch sources for a movie so Watch now is instant (not on metered data saver)
        if (value is MovieLoadResponse && !Net.shouldSaveData(context)) fetchLinks(value.dataUrl)
    }

    // ---- derived data ----
    val dubOptions: List<DubStatus> = (loaded as? AnimeLoadResponse)?.episodes?.keys?.toList().orEmpty()
    val episodes: List<Episode> = when (loaded) {
        is TvSeriesLoadResponse -> loaded.episodes
        is AnimeLoadResponse -> {
            val key = dub?.takeIf { loaded.episodes.containsKey(it) } ?: loaded.episodes.keys.firstOrNull()
            key?.let { loaded.episodes[it] }.orEmpty()
        }
        else -> emptyList()
    }
    val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
    val activeSeason = if (seasons.size > 1) (season?.takeIf { it in seasons } ?: seasons.first()) else null
    val shownEpisodes = if (activeSeason == null) episodes else episodes.filter { it.season == activeSeason }

    fun primaryData(): String = when {
        loaded is MovieLoadResponse -> loaded.dataUrl
        else -> selectedEpisode?.data ?: episodes.firstOrNull()?.data ?: url
    }

    fun download(link: ExtractorLink) {
        if (link.type == ExtractorLinkType.MAGNET || link.type == ExtractorLinkType.TORRENT) {
            say("Torrent downloads are not supported")
            return
        }
        Downloader.start(
            context,
            Downloader.Item(
                id = Integer.toHexString(link.url.hashCode()),
                title = displayTitle,
                poster = poster,
                url = link.url,
                referer = link.referer,
                headers = link.headers,
                type = if (link.type == ExtractorLinkType.M3U8) "M3U8" else "VIDEO",
                quality = link.quality,
                provider = provider?.name ?: ""
            )
        )
        say("Download started — see Library")
    }

    fun watchNow() {
        val data = primaryData()
        val ready = linkCache[data]
        if (ready != null && ready.isNotEmpty()) {
            links = ready
            playLink(ready.first())
        } else {
            autoPlay = true
            showSources = true
            fetchLinks(data)
        }
    }

    // ---- UI ----
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            // ---- header: artwork, scrim, title ----
            item {
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    PosterArt(
                        loaded?.backgroundPosterUrl ?: loaded?.posterUrl ?: poster,
                        displayTitle,
                        Modifier.fillMaxSize().sharedPoster(ctx, sharedKey)
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.35f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.4f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }

                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)) {
                        Text(
                            displayTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOfNotNull(
                            loaded?.year?.toString(),
                            loaded?.type?.name,
                            loaded?.duration?.takeIf { it > 0 }?.let { "$it min" }
                        ).joinToString("  •  ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (loading) {
                item { WavyProgress(null, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }

            if (loadError != null && loaded == null) {
                item {
                    EmptyState(
                        icon = Icons.Filled.CloudOff,
                        title = "Could not load",
                        body = loadError.orEmpty(),
                        actionLabel = "Retry",
                        onAction = { reloadTick++ }
                    )
                }
            }

            if (loaded != null) {
                // ---- actions ----
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { watchNow() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (linksLoading) "Finding sources…" else "Watch now")
                        }
                        FilledTonalIconButton(onClick = {
                            val ready = linkCache[primaryData()]
                            if (!ready.isNullOrEmpty()) download(ready.first())
                            else {
                                autoPlay = false
                                showSources = true
                                fetchLinks(primaryData())
                            }
                        }) { Icon(Icons.Filled.Download, contentDescription = "Download") }
                        FilledTonalIconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Watch $displayTitle\n${loaded.url}")
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    }
                }

                // ---- tags + plot ----
                loaded.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                    item {
                        Text(
                            tags.take(5).joinToString("  •  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                val plot = loaded.plot
                if (!plot.isNullOrBlank()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
                            Text(
                                plot,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
                        }
                    }
                }

                // ---- episodes ----
                if (episodes.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader("Episodes (${shownEpisodes.size})")
                            if (dubOptions.size > 1 || seasons.size > 1) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (dubOptions.size > 1) {
                                        val activeDub = dub?.takeIf { it in dubOptions } ?: dubOptions.first()
                                        items(dubOptions) { option ->
                                            FilterChip(
                                                selected = option == activeDub,
                                                onClick = { dub = option; season = null },
                                                label = { Text(option.name) }
                                            )
                                        }
                                    }
                                    if (seasons.size > 1) {
                                        items(seasons) { s ->
                                            FilterChip(
                                                selected = s == activeSeason,
                                                onClick = { season = s },
                                                label = { Text("Season $s") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(shownEpisodes) { index, episode ->
                        val label = buildString {
                            episode.episode?.let { append("E$it  ") }
                            append(episode.name?.takeIf { it.isNotBlank() } ?: "Episode ${episode.episode ?: index + 1}")
                        }
                        ListItem(
                            headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val sub = episode.description?.takeIf { it.isNotBlank() }
                                    ?: episode.runTime?.let { "$it min" }
                                if (sub != null) Text(sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = if (!episode.posterUrl.isNullOrBlank()) {
                                {
                                    PosterArt(
                                        episode.posterUrl, label,
                                        Modifier.size(width = 96.dp, height = 54.dp).clip(MaterialTheme.shapes.small),
                                        showTitleFallback = false
                                    )
                                }
                            } else null,
                            trailingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = "Sources") },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selectedEpisode == episode) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            ),
                            modifier = Modifier.clickable {
                                selectedEpisode = episode
                                autoPlay = false
                                showSources = true
                                fetchLinks(episode.data)
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    // ---- sources sheet ----
    if (showSources) {
        ModalBottomSheet(onDismissRequest = {
            showSources = false
            autoPlay = false
        }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Sources",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    selectedEpisode?.takeIf { loaded !is MovieLoadResponse }?.let { it.name ?: "Episode ${it.episode ?: ""}" }
                        ?: displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                if (linksLoading) WavyProgress(null, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                if (linksError != null && !linksLoading) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text(linksError.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { sourcesFor?.let { fetchLinks(it, force = true) } }) { Text("Try again") }
                    }
                }
                links.forEach { link ->
                    ListItem(
                        headlineContent = { Text(link.name) },
                        supportingContent = { Text("${link.type.name}  •  ${link.quality}p") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { download(link) }) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download")
                                }
                                FilledTonalIconButton(onClick = { playLink(link) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { playLink(link) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
