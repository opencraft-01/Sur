package com.primebox.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.primebox.data.Card
import com.primebox.data.HomeCache
import com.primebox.data.Net
import com.primebox.ext.Languages
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.absoluteValue

private const val CACHE_TTL_MS = 30L * 60L * 1000L

/**
 * CineGlass home: one screen-wide hero card per page (art dissolving into the
 * background), glass chips, then rows of poster tiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    refreshKey: Int,
    randomTick: Int,
    ctx: SharedCtx? = null,
    onOpenDetail: (Card) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSwitcher: () -> Unit
) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(Providers.active()) }
    var rows by remember { mutableStateOf<List<HomeCache.Row>>(emptyList()) }
    var loading by remember { mutableStateOf(rows.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var sheetRow by remember { mutableStateOf<HomeCache.Row?>(null) }

    LaunchedEffect(refreshKey) { provider = Providers.active() }

    LaunchedEffect(provider?.name, reload) {
        val api = provider ?: return@LaunchedEffect
        val cached = HomeCache.load(context, api.name)
        if (cached != null && cached.rows.isNotEmpty()) {
            rows = cached.rows
            loading = false
        }
        val fresh = cached != null && System.currentTimeMillis() - cached.time < CACHE_TTL_MS
        if (fresh && reload == 0) return@LaunchedEffect

        loading = rows.isEmpty()
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
        val response = result.getOrNull()
        if (response == null || response.items.isEmpty()) {
            error = result.exceptionOrNull()?.message ?: "Could not load"
            return@LaunchedEffect
        }
        val loaded = response.items.map { list ->
            HomeCache.Row(list.name, list.list.map { Card.from(it) }, list.isHorizontalImages)
        }.sortedBy { rowPriority(it.name) }
        rows = loaded
        HomeCache.save(context, api.name, loaded)
    }

    val visibleRows = remember(rows, selectedType) {
        val type = selectedType
        if (type == null) rows else rows.mapNotNull { row ->
            val filtered = row.items.filter { it.type.equals(type, true) }
            if (filtered.isEmpty()) null else row.copy(items = filtered)
        }
    }
    val heroItems = visibleRows.firstOrNull()?.items?.take(5).orEmpty()

    LaunchedEffect(randomTick) {
        if (randomTick <= 0) return@LaunchedEffect
        val pool = visibleRows.flatMap { it.items }
        if (pool.isNotEmpty()) onOpenDetail(pool.random())
    }

    val continueWatching = remember(refreshKey) {
        com.primebox.data.Store.history().take(12).map {
            Card(it.title, it.url, it.poster, null, null, it.provider)
        }
    }

    sheetRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { sheetRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Cine.c.sheet
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(row.name, color = Cine.c.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${row.items.size} titles", color = Cine.c.textMuted, fontSize = 11.5.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                ) {
                    gridItems(row.items) { card ->
                        MovieCard(
                            card = card,
                            onClick = {
                                sheetRow = null
                                onOpenDetail(card)
                            },
                            width = 104.dp,
                            height = 156.dp
                        )
                    }
                }
            }
        }
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            item {
                HomeTopBar(onOpenSearch, onOpenExtensions, onOpenSettings)
            }

            if (heroItems.isNotEmpty()) {
                item { HeroCarousel(heroItems, ctx, onOpenDetail) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip(
                        text = if (provider == null) "No extension" else "${Languages.flag(provider?.lang)}  ${provider?.name}",
                        onClick = onOpenSwitcher
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Languages.name(provider?.lang).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Cine.c.textMuted
                    )
                }
            }

            item {
                TypeChips(
                    api = provider,
                    rows = rows,
                    selected = selectedType,
                    onSelect = { selectedType = it },
                    onRandom = {
                        val pool = visibleRows.flatMap { it.items }
                        if (pool.isNotEmpty()) onOpenDetail(pool.random())
                    }
                )
            }

            if (loading && rows.isEmpty()) {
                item {
                    Row(Modifier.padding(start = 20.dp, top = 18.dp)) {
                        repeat(3) {
                            MovieCardSkeleton()
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
            } else if (error != null && rows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(Modifier.glass(RoundedCornerShape(22.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Could not load", color = Cine.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(error ?: "", color = Cine.c.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))
                            GlowButton("Retry", onClick = { reload++ })
                        }
                    }
                }
            } else if (visibleRows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(Modifier.glass(RoundedCornerShape(22.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nothing here yet", color = Cine.c.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Add a repository or import a .cs3 file, then pick a source.",
                                color = Cine.c.textMuted,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            GlowButton("Open extensions", onClick = { onOpenExtensions() })
                        }
                    }
                }
            } else {
                if (continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        CineSectionHeader("Continue watching")
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(continueWatching) { card ->
                                EpisodeCard(card, onClick = { onOpenDetail(card) }, subtitle = card.api, ctx = ctx)
                            }
                        }
                    }
                }

                visibleRows.forEach { row ->
                    item(key = row.name) {
                        CineSectionHeader(row.name, onSeeAll = { sheetRow = row })
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(row.items) { card ->
                                if (row.horizontal) {
                                    EpisodeCard(card, onClick = { onOpenDetail(card) }, subtitle = card.type, ctx = ctx)
                                } else {
                                    MovieCard(card, onClick = { onOpenDetail(card) }, ctx = ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSearch: () -> Unit, onOpenExtensions: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PrimeBox",
                color = Cine.c.text,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                style = glowText(Cine.c.accent.copy(alpha = 0.55f)),
                modifier = Modifier.weight(1f)
            )
            GlassIconButton(Icons.Filled.Settings, onOpenSettings, size = 38.dp)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Filled.Layers, onOpenExtensions, size = 38.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .glass(RoundedCornerShape(50))
                .clickable { onOpenSearch() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = Cine.c.textMuted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Text("Search movies, series, anime…", color = Cine.c.textMuted, fontSize = 13.sp)
        }
    }
}

/** Screen-wide hero card: art edge to edge, dissolving into the page background. */
@Composable
private fun HeroCarousel(items: List<Card>, ctx: SharedCtx?, onOpenDetail: (Card) -> Unit) {
    val state = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5200)
            state.animateScrollToPage((state.currentPage + 1) % items.size)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = 22.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(430.dp)
        ) { page ->
            val card = items[page]
            val offset = (state.currentPage - page).absoluteValue
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = if (offset == 0) 1f else 0.94f
                        scaleX = scale
                        scaleY = scale
                        alpha = if (offset == 0) 1f else 0.72f
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .clickable { onOpenDetail(card) }
            ) {
                PosterArt(card, Modifier.fillMaxSize(), ctx = ctx)
                // artwork dissolving into the page background
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color(0x33000000),
                            0.75f to Color(0xCC0A0A0D),
                            1f to Cine.c.bgBottom
                        )
                    )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QualityBadge(if (page < 10) "TOP ${page + 1}" else "TRENDING", Modifier)
                        Spacer(Modifier.width(6.dp))
                        if (!card.quality.isNullOrBlank()) QualityBadge(card.quality, Modifier)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        card.name,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 25.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = glowText(Color.Black.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(card.type, card.api).joinToString("   •   "),
                        color = Color(0xFFD6D8DE),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowButton("Watch now", onClick = { onOpenDetail(card) }, icon = Icons.Filled.PlayArrow)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(38.dp).glass(CircleShape).clickable { onOpenDetail(card) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (index == state.currentPage) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (index == state.currentPage) Cine.c.accent else Cine.c.textMuted.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun TypeChips(
    api: MainAPI?,
    rows: List<HomeCache.Row>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onRandom: () -> Unit
) {
    val types = remember(api, rows) {
        val set = linkedSetOf<String>()
        api?.supportedTypes?.forEach { set.add(it.name) }
        rows.forEach { row -> row.items.forEach { card -> card.type?.let { set.add(it) } } }
        set.toList()
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item { GlassChip("Random", leading = Icons.Filled.Shuffle, onClick = onRandom) }
        item { GlassChip("All", selected = selected == null, onClick = { onSelect(null) }) }
        items(types) { type ->
            GlassChip(type, selected = selected == type, onClick = { onSelect(type) })
        }
    }
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
