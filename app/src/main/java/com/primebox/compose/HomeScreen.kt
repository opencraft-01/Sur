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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

private const val CACHE_TTL_MS = 30L * 60L * 1000L

/**
 * CloudStream-style home: search bar on top, hero carousel, type chips, then rows
 * of 114x180 cards with the title inside the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    refreshKey: Int,
    randomTick: Int,
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

    LaunchedEffect(refreshKey) {
        provider = Providers.active()
    }

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

    val api = provider
    val visibleRows = remember(rows, selectedType) {
        val type = selectedType
        if (type == null) rows else rows.mapNotNull { row ->
            val filtered = row.items.filter { it.type.equals(type, true) }
            if (filtered.isEmpty()) null else row.copy(items = filtered)
        }
    }
    val heroItems = visibleRows.firstOrNull()?.items?.take(5).orEmpty()

    // CloudStream's Random button: open a random title from the loaded rows
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

    // "See all" popup: same idea as CloudStream's row "more info" bottom sheet
    sheetRow?.let { row ->
        ModalBottomSheet(
            onDismissRequest = { sheetRow = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "${row.items.size} titles",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                ) {
                    gridItems(row.items) { card ->
                        PosterCard(card, width = 104, height = 150) {
                            sheetRow = null
                            onOpenDetail(card)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                providerName = api?.name,
                providerLang = api?.lang,
                onOpenSearch = onOpenSearch,
                onOpenExtensions = onOpenExtensions,
                onOpenSettings = onOpenSettings
            )
        }

        if (heroItems.isNotEmpty()) {
            item { HeroCarousel(heroItems, onOpenDetail) }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillChip(
                    text = if (api == null) "No extension" else "${Languages.flag(api.lang)}  ${api.name}",
                    onClick = onOpenSwitcher
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = Languages.name(api?.lang).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            TypeChips(
                api = api,
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
            items(3) { RowSkeleton() }
        } else if (error != null && rows.isEmpty()) {
            item {
                EmptyState(
                    title = "Could not load",
                    body = error ?: "Unknown error",
                    actionLabel = "Retry"
                ) { reload++ }
            }
        } else if (visibleRows.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here yet",
                    body = "Add an extension repository or import a .cs3 file, then pick a source with the button below.",
                    actionLabel = "Open extensions",
                    onAction = onOpenExtensions
                )
            }
        } else {
            if (continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    SectionHeader("Continue watching")
                    LazyRow(
                        contentPadding = PaddingValues(start = 15.dp, end = 5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(continueWatching) { card ->
                            PosterCard(card = card, width = 114, height = 180) { onOpenDetail(card) }
                        }
                    }
                }
            }
            visibleRows.forEach { row ->
                item(key = row.name) {
                    SectionHeader(row.name, "›") { sheetRow = row }
                    LazyRow(
                        contentPadding = PaddingValues(start = 15.dp, end = 5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(row.items) { card ->
                            PosterCard(
                                card = card,
                                width = if (row.horizontal) 200 else 114,
                                height = if (row.horizontal) 114 else 180
                            ) { onOpenDetail(card) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    providerName: String?,
    providerLang: String?,
    onOpenSearch: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PrimeBox",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { onOpenSettings() }.padding(8.dp).size(20.dp)
            )
            androidx.compose.material3.Icon(
                Icons.Filled.Layers,
                contentDescription = "Extensions",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { onOpenExtensions() }.padding(8.dp).size(20.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onOpenSearch() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Search movies, series, anime…",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Google-TV style ranked carousel that auto-scrolls right to left. */
@Composable
private fun HeroCarousel(items: List<Card>, onOpenDetail: (Card) -> Unit) {
    val state = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            val next = (state.currentPage + 1) % items.size
            state.animateScrollToPage(next)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = 14.dp),
            pageSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) { page ->
            val card = items[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onOpenDetail(card) }
            ) {
                if (card.poster.isNullOrBlank()) {
                    ShimmerBox(Modifier.fillMaxSize(), corner = 22)
                } else {
                    AsyncImage(
                        model = card.poster,
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color(0x660A0A0D),
                                1f to Color(0xF70A0A0D)
                            )
                        )
                )
                Badge(
                    text = if (page < 10) "TOP ${page + 1}" else "TRENDING",
                    color = if (page < 3) OnAccent else Color.White,
                    background = if (page < 3) Accent else Color.Black.copy(alpha = 0.6f)
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        card.name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(card.type, card.quality).joinToString("   •   "),
                        color = Color(0xFFC9CDD6),
                        fontSize = 12.sp
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(items.size) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (index == state.currentPage) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == state.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
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
        item { IconChip(Icons.Filled.Shuffle, "Random", false) { onRandom() } }
        item { PillChip("All", selected == null) { onSelect(null) } }
        items(types) { type ->
            PillChip(type, selected == type) { onSelect(type) }
        }
    }
}

@Composable
private fun RowSkeleton() {
    Column(Modifier.padding(start = 20.dp, top = 16.dp)) {
        ShimmerBox(Modifier.width(140.dp).height(16.dp), corner = 8)
        Spacer(Modifier.height(12.dp))
        Row {
            repeat(3) {
                ShimmerBox(Modifier.width(114.dp).height(180.dp), corner = 14)
                Spacer(Modifier.width(10.dp))
            }
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
