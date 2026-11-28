@file:OptIn(ExperimentalMaterial3Api::class)

package com.primebox.compose

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.primebox.data.Card
import com.primebox.data.HomeCache
import com.primebox.data.Net
import com.primebox.data.Store
import com.primebox.ext.ExtensionManager
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val CACHE_TTL_MS = 30L * 60L * 1000L

/** Rows per provider kept for the life of the process, so coming back from a detail page is instant. */
private object HomeSession {
    val rows = HashMap<String, List<HomeCache.Row>>()
}

/**
 * Streaming-style home: a full-bleed auto-sliding hero under a transparent top bar (logo,
 * source pill, category chips), then Top 10 / landscape / poster rows and a flat bottom
 * navigation. Built from Material 3 components; pull down to refresh.
 */
@Composable
fun HomeScreen(
    refreshKey: Int,
    listState: LazyListState,
    ctx: SharedCtx?,
    snackbar: SnackbarHostState,
    onOpenDetail: (Card, String) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSwitcher: () -> Unit
) {
    val context = LocalContext.current
    val revision by ExtensionManager.revision.collectAsStateWithLifecycle()
    val extensionsReady by ExtensionManager.ready.collectAsStateWithLifecycle()

    // Extensions load on a worker thread at start-up; re-resolve once they are ready
    // (previously the home screen stayed on "no extension" until something else changed).
    var provider by remember { mutableStateOf<MainAPI?>(Providers.active()) }
    LaunchedEffect(refreshKey, revision) { provider = Providers.active() }
    val providerName = provider?.name

    var rows by remember(providerName) {
        mutableStateOf(providerName?.let { HomeSession.rows[it] }.orEmpty())
    }
    var loading by remember(providerName) { mutableStateOf(providerName != null && rows.isEmpty()) }
    var error by remember(providerName) { mutableStateOf<String?>(null) }
    var selectedType by remember(providerName) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var handledReload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var sheetRow by remember { mutableStateOf<HomeCache.Row?>(null) }

    LaunchedEffect(providerName, reload) {
        val api = provider
        if (api == null) {
            loading = false
            return@LaunchedEffect
        }
        val manual = reload != handledReload
        handledReload = reload

        // disk cache is read off the main thread
        val cached = withContext(Dispatchers.IO) { HomeCache.load(context, api.name) }
        if (cached != null && cached.rows.isNotEmpty() && rows.isEmpty()) {
            rows = cached.rows
            HomeSession.rows[api.name] = cached.rows
        }
        val fresh = cached != null && cached.rows.isNotEmpty() &&
            System.currentTimeMillis() - cached.time < CACHE_TTL_MS
        if (fresh && !manual) {
            loading = false
            return@LaunchedEffect
        }

        loading = rows.isEmpty()
        refreshing = manual
        error = null
        val result = withContext(Dispatchers.IO) {
            if (!Net.isOnline(context)) return@withContext Result.failure(IllegalStateException("You are offline"))
            runCatching {
                val page = api.mainPage.firstOrNull() ?: MainPageData("", "", false)
                withTimeout(25_000) {
                    api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                }
            }
        }
        loading = false
        refreshing = false
        val response = result.getOrNull()
        if (response == null || response.items.isEmpty()) {
            val message = result.exceptionOrNull()?.message ?: "Nothing came back from ${api.name}"
            if (rows.isEmpty()) error = message else snackbar.showSnackbar("Couldn't refresh — showing saved content")
            return@LaunchedEffect
        }
        val loaded = response.items.map { list ->
            HomeCache.Row(list.name, list.list.map { Card.from(it) }, list.isHorizontalImages)
        }.sortedBy { rowPriority(it.name) }
        rows = loaded
        HomeSession.rows[api.name] = loaded
        withContext(Dispatchers.IO) { HomeCache.save(context, api.name, loaded) }
    }

    val visibleRows = remember(rows, selectedType) {
        val type = selectedType
        if (type == null) rows else rows.mapNotNull { row ->
            val filtered = row.items.filter { it.type.equals(type, true) }
            if (filtered.isEmpty()) null else row.copy(items = filtered)
        }
    }
    // rotate the hero window on every reload so the banners change instead of repeating
    val heroItems = remember(visibleRows, reload) {
        val pool = visibleRows.flatMap { it.items }
        if (pool.isEmpty()) emptyList()
        else (0 until minOf(6, pool.size)).map { pool[(it + reload * 3) % pool.size] }
    }

    val continueWatching = remember(refreshKey) {
        Store.history().take(12).map { Card(it.title, it.url, it.poster, null, null, it.provider) }
    }

    sheetRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { sheetRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${row.items.size} titles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(row.items) { card ->
                        PosterCard(
                            card = card,
                            width = null,
                            onClick = {
                                sheetRow = null
                                onOpenDetail(card, "grid|${row.name}|${card.url}")
                            }
                        )
                    }
                }
            }
        }
    }

    val solidBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 360 }
    }
    val scrim by animateFloatAsState(if (solidBar) 0f else 1f, label = "barScrim")
    val surface = MaterialTheme.colorScheme.surface
    val top10Index = remember(visibleRows) {
        visibleRows.indexOfFirst { r ->
            val n = r.name.lowercase()
            n.contains("trending") || n.contains("top") || n.contains("popular")
        }.takeIf { it >= 0 } ?: 0
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { reload++ },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when {
                    heroItems.isNotEmpty() -> item(key = "hero") {
                        HeroPager(heroItems, ctx) { card, key -> onOpenDetail(card, key) }
                    }
                    loading && rows.isEmpty() -> item(key = "hero-skeleton") {
                        Box(Modifier.fillMaxWidth().height(HERO_HEIGHT).shimmer())
                    }
                    else -> item(key = "bar-space") { Spacer(Modifier.height(150.dp)) }
                }

                when {
                    provider == null && !extensionsReady -> item { SkeletonRow() }

                    provider == null -> item {
                        EmptyState(
                            icon = Icons.Filled.Extension,
                            title = "No extension yet",
                            body = "Add a repository or import a .cs3 file, then pick a source.",
                            actionLabel = "Open extensions",
                            onAction = onOpenExtensions
                        )
                    }

                    loading && rows.isEmpty() -> item { SkeletonRow() }

                    error != null && rows.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Filled.CloudOff,
                            title = "Could not load",
                            body = error.orEmpty(),
                            actionLabel = "Retry",
                            onAction = { reload++ }
                        )
                    }

                    visibleRows.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Filled.VideoLibrary,
                            title = "Nothing here",
                            body = "This source returned no titles for the selected filter."
                        )
                    }

                    else -> {
                        if (continueWatching.isNotEmpty()) {
                            item(key = "continue") {
                                Column {
                                    SectionHeader("Continue Watching")
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(continueWatching) { card ->
                                            val key = "continue|${card.url}"
                                            LandscapeCard(
                                                card, onClick = { onOpenDetail(card, key) },
                                                subtitle = card.api, ctx = ctx, sharedKey = key
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        itemsIndexed(visibleRows) { index, row ->
                            Column {
                                SectionHeader(
                                    if (index == top10Index) "Top 10 • ${row.name}" else row.name,
                                    onSeeAll = { sheetRow = row }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(if (index == top10Index) 4.dp else 10.dp)
                                ) {
                                    if (index == top10Index && !row.horizontal) {
                                        itemsIndexed(row.items.take(10)) { rank, card ->
                                            val key = "${row.name}|${card.url}"
                                            Top10Card(rank + 1, card, ctx, key) { onOpenDetail(card, key) }
                                        }
                                    } else {
                                        items(row.items) { card ->
                                            val key = "${row.name}|${card.url}"
                                            if (row.horizontal) {
                                                LandscapeCard(
                                                    card, onClick = { onOpenDetail(card, key) },
                                                    subtitle = card.type, ctx = ctx, sharedKey = key
                                                )
                                            } else {
                                                PosterCard(
                                                    card, onClick = { onOpenDetail(card, key) },
                                                    ctx = ctx, sharedKey = key, showTitle = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // transparent over the hero, solid once the list scrolls
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f * scrim), Color.Transparent)))
                    drawRect(surface.copy(alpha = 1f - scrim))
                }
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PrimeBox",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onOpenSwitcher,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(36.dp).widthIn(max = 180.dp)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(providerName ?: "Source", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = { reload++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            TypeChips(
                api = provider,
                rows = rows,
                selected = selectedType,
                onSelect = { selectedType = it },
                onRandom = {
                    val pool = visibleRows.flatMap { it.items }
                    if (pool.isNotEmpty()) {
                        val card = pool.random()
                        onOpenDetail(card, "random|${card.url}")
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

private val HERO_HEIGHT = 560.dp

/** Full-bleed hero: swipeable, auto-advances every 5 s (paused while dragging), dot indicator. */
@Composable
private fun HeroPager(items: List<Card>, ctx: SharedCtx?, onOpen: (Card, String) -> Unit) {
    val pager = rememberPagerState { items.size }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(items, dragged) {
        if (items.size < 2 || dragged) return@LaunchedEffect
        while (true) {
            delay(5000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val card = items[page]
            val key = "hero|${card.url}"
            Box(Modifier.fillMaxSize().clickable { onOpen(card, key) }) {
                PosterArt(card.poster, card.name, Modifier.fillMaxSize().sharedPoster(ctx, key))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.30f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to surface
                        )
                    )
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(card.type, card.quality).joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onOpen(card, key) },
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Watch Now", style = MaterialTheme.typography.titleSmall)
                        }
                        FilledTonalIconButton(onClick = { onOpen(card, key) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = "Details")
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(items.size) { i ->
                    val active = pager.currentPage == i
                    val width by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 0.95f else 0.35f))
                    )
                }
            }
        }
    }
}

/** "Top 10" tile: a large outlined rank numeral tucked behind the poster. */
@Composable
private fun Top10Card(rank: Int, card: Card, ctx: SharedCtx?, key: String, onClick: () -> Unit) {
    Box(Modifier.height(168.dp)) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = 5f, join = StrokeJoin.Round)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 2.dp, y = 14.dp)
        )
        PosterCard(
            card, onClick = onClick, width = 112.dp, ctx = ctx, sharedKey = key, showTitle = false,
            modifier = Modifier.align(Alignment.BottomEnd).padding(start = if (rank >= 10) 96.dp else 62.dp)
        )
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        Modifier.padding(start = 16.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) { PosterSkeleton() }
    }
}

@Composable
private fun TypeChips(
    api: MainAPI?,
    rows: List<HomeCache.Row>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val types = remember(api, rows) {
        val set = linkedSetOf<String>()
        api?.supportedTypes?.forEach { set.add(it.name) }
        rows.forEach { row -> row.items.forEach { card -> card.type?.let { set.add(it) } } }
        set.toList()
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            AssistChip(
                onClick = onRandom,
                label = { Text("Random") },
                leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            )
        }
        item { TypeChip("All", selected == null) { onSelect(null) } }
        items(types) { type -> TypeChip(type, selected == type) { onSelect(type) } }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

/** Trending / language rows float to the top, like CloudStream's ordered home. */
private fun rowPriority(name: String): Int {
    val n = name.lowercase()
    var score = 100
    if (n.contains("trending") || n.contains("top") || n.contains("popular") || n.contains("hot")) score -= 40
    if (n.contains("new") || n.contains("latest") || n.contains("recent") || n.contains("release")) score -= 20
    if (n.contains("hindi")) score -= 16
    if (n.contains("bengali") || n.contains("bangla")) score -= 15
    if (n.contains("tamil") || n.contains("telugu") || n.contains("malayalam") || n.contains("kannada")) score -= 13
    if (n.contains("dubbed") || n.contains("web series")) score -= 6
    if (n.contains("movie")) score -= 5
    if (n.contains("anime")) score += 4
    return score
}
