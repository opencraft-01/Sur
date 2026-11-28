@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.primebox.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primebox.data.Card
import com.primebox.data.Net
import com.primebox.data.Store
import com.primebox.data.Updater
import com.primebox.ext.Languages
import com.primebox.ext.Providers

sealed interface Route {
    data object Home : Route
    data object Library : Route
    data object Search : Route
    data object Extensions : Route
    data object Settings : Route
    data class Detail(val url: String, val name: String, val poster: String?, val api: String?) : Route
    data class Player(
        val url: String,
        val referer: String?,
        val title: String,
        val source: String,
        val headers: Map<String, String>
    ) : Route
}

private data class NavItem(val icon: ImageVector, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimeBoxRoot(
    themeMode: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    var refreshKey by remember { mutableStateOf(0) }
    var randomTick by remember { mutableStateOf(0) }
    var showSwitcher by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Updater.Info?>(null) }

    // Scroll positions live above the navigation graph, so coming back from a
    // detail page restores exactly where the user was instead of jumping to top.
    val homeListState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val libraryListState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    val current = stack.last()
    val switcherState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    // silent update check, at most once a day and never on mobile data
    LaunchedEffect(Unit) {
        if (Net.shouldSaveData(context)) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - Store.lastUpdateCheck < 24L * 60L * 60L * 1000L) return@LaunchedEffect
        Store.lastUpdateCheck = now
        val info = Updater.check() ?: return@LaunchedEffect
        if (info.versionCode <= Updater.currentCode()) return@LaunchedEffect
        if (Store.lastPromptedUpdate >= info.versionCode) return@LaunchedEffect
        Store.lastPromptedUpdate = info.versionCode
        updateInfo = info
    }

    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = current,
                label = "route",
                transitionSpec = {
                    when {
                        // details rides up like a sheet, then drops away on back
                        // the poster flies between screens, so the page itself only fades
                        targetState is Route.Detail ->
                            fadeIn(tween(260)) togetherWith fadeOut(tween(160))

                        initialState is Route.Detail ->
                            fadeIn(tween(240)) togetherWith fadeOut(tween(180))

                        else -> fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    }
                }
            ) { r ->
                val ctx = SharedCtx(this@SharedTransitionLayout, this)
                Box(Modifier.fillMaxSize()) {
                    when (r) {
                        is Route.Home -> HomeScreen(
                            refreshKey = refreshKey,
                            randomTick = randomTick,
                            ctx = ctx,
                            listState = homeListState,
                            onOpenDetail = { card ->
                                stack.add(Route.Detail(card.url, card.name, card.poster, card.api))
                            },
                            onOpenSearch = { stack.add(Route.Search) },
                            onOpenExtensions = { stack.add(Route.Extensions) },
                            onOpenSettings = { stack.add(Route.Settings) },
                            onOpenSwitcher = { showSwitcher = true }
                        )

                        is Route.Library -> LibraryScreen(
                            listState = libraryListState,
                            onOpenDetail = { url, name, poster, api ->
                                stack.add(Route.Detail(url, name, poster, api))
                            },
                            onPlayLocal = { file, title ->
                                stack.add(
                                    Route.Player(
                                        url = android.net.Uri.fromFile(file).toString(),
                                        referer = null,
                                        title = title,
                                        source = "Downloaded",
                                        headers = emptyMap()
                                    )
                                )
                            }
                        )

                        is Route.Search -> SearchScreen(
                            onOpenDetail = { card -> stack.add(Route.Detail(card.url, card.name, card.poster, card.api)) },
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )

                        is Route.Extensions -> ExtensionsScreen(onBack = {
                            refreshKey++
                            stack.removeAt(stack.lastIndex)
                        })

                        is Route.Settings -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeChange = { onThemeChange(it) },
                            onShowUpdate = { updateInfo = it },
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )

                        is Route.Detail -> DetailSheetScreen(
                            url = r.url,
                            title = r.name,
                            poster = r.poster,
                            apiName = r.api,
                            quality = null,
                            ctx = ctx,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onPlay = { link, title ->
                                stack.add(
                                    Route.Player(
                                        url = link.url,
                                        referer = link.referer,
                                        title = title,
                                        source = "${link.name}  •  ${link.quality}p",
                                        headers = link.headers
                                    )
                                )
                            }
                        )

                        is Route.Player -> PlayerScreen(
                            url = r.url,
                            referer = r.referer,
                            title = r.title,
                            source = r.source,
                            headers = r.headers,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                    }
                }
            }
        }

        // floating glass nav bar (CineGlass "Floating" style)
        if (current is Route.Home || current is Route.Library) {
            FloatingNavBar(
                selected = if (current is Route.Home) 0 else 1,
                onSelect = { index ->
                    if (index == 0) {
                        if (stack.size > 1) stack.removeRange(1, stack.size) else stack[0] = Route.Home
                    } else {
                        if (stack.size > 1) stack.removeRange(1, stack.size)
                        stack.add(Route.Library)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // source switcher FAB
        if (current is Route.Home) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 22.dp, bottom = 104.dp)
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .shadow(16.dp, CircleShape, ambientColor = Cine.c.accent, spotColor = Cine.c.accent)
                        .clip(CircleShape)
                        .background(Cine.c.accent)
                        .clickable { showSwitcher = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = "Source", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    if (showSwitcher) {
        ModalBottomSheet(
            onDismissRequest = { showSwitcher = false },
            sheetState = switcherState,
            containerColor = Cine.c.sheet
        ) {
            ExtensionSwitcher(
                onPick = {
                    showSwitcher = false
                    refreshKey++
                },
                onManage = {
                    showSwitcher = false
                    stack.add(Route.Extensions)
                }
            )
        }
    }

    updateInfo?.let { info ->
        UpdateSheet(info) { updateInfo = null }
    }
}

/** Floating glass pill nav bar with a sliding accent disc. */
@Composable
private fun FloatingNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Cine.c
    val items = remember {
        listOf(
            NavItem(Icons.Filled.Home, "Home"),
            NavItem(Icons.Filled.VideoLibrary, "Library")
        )
    }
    Box(modifier.navigationBarsPadding().padding(horizontal = 44.dp, vertical = 14.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(64.dp).glass(RoundedCornerShape(32.dp), strong = true)
        ) {
            val tabW = maxWidth / items.size
            val x by animateDpAsState(
                targetValue = tabW * selected + (tabW - 46.dp) / 2,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                label = "pill"
            )
            Box(
                Modifier
                    .offset { IntOffset(x.roundToPx(), 9.dp.roundToPx()) }
                    .size(46.dp)
                    .shadow(14.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                    .clip(CircleShape)
                    .background(c.accent)
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected == i) Color.White else c.textMuted,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionSwitcher(onPick: () -> Unit, onManage: () -> Unit) {
    val providers = remember { Providers.all() }
    var query by remember { mutableStateOf("") }
    val filtered = providers.filter { query.isBlank() || it.name.contains(query, true) }
    val active = Store.activeProvider

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Choose extension", color = Cine.c.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            "The home screen is built from the selected source",
            color = Cine.c.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search extensions", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Text(
                "No extension found. Open Extensions to add a repository or import a .cs3 file.",
                color = Cine.c.textMuted,
                fontSize = 12.5.sp
            )
        }

        filtered.take(20).forEach { api ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .then(
                        if (api.name == active) Modifier.clip(RoundedCornerShape(16.dp)).background(Cine.c.accent.copy(alpha = 0.18f))
                        else Modifier.glass(RoundedCornerShape(16.dp))
                    )
                    .clickable {
                        Providers.select(api)
                        onPick()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(api.name, color = Cine.c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        api.mainUrl.removePrefix("https://").removePrefix("http://"),
                        color = Cine.c.textMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
                GlassChip("${Languages.flag(api.lang)}  ${Languages.name(api.lang)}")
            }
        }

        Spacer(Modifier.height(6.dp))
        GlowButton("Manage extensions", onClick = onManage, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
    }
}
