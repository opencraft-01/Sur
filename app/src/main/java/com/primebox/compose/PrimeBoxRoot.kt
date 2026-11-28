@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)

package com.primebox.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primebox.data.Card
import com.primebox.data.Net
import com.primebox.data.Store
import com.primebox.data.Updater
import com.primebox.ext.ExtensionManager
import com.primebox.ext.Languages
import com.primebox.ext.Providers

sealed interface Route {
    data object Home : Route
    data object Library : Route
    data object Search : Route
    data object Extensions : Route
    data object Settings : Route
    data class Detail(
        val url: String,
        val name: String,
        val poster: String?,
        val api: String?,
        /** unique key of the poster that was tapped, so the shared element matches */
        val sharedKey: String
    ) : Route

    data class Player(
        val url: String,
        val referer: String?,
        val title: String,
        val source: String,
        val headers: Map<String, String>
    ) : Route
}

private data class Tab(val route: Route, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Bottom navigation: Home · Search · Downloads · Extensions · My Space (settings). */
private val tabs = listOf(
    Tab(Route.Home, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Route.Search, "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab(Route.Library, "Downloads", Icons.Outlined.Download, Icons.Filled.Download),
    Tab(Route.Extensions, "Sources", Icons.Outlined.Extension, Icons.Filled.Extension),
    Tab(Route.Settings, "My Space", Icons.Outlined.Person, Icons.Filled.Person)
)

@Composable
fun PrimeBoxRoot(
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showSwitcher by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Updater.Info?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Scroll positions live above the navigation graph, so coming back from a detail
    // page restores exactly where the user was instead of jumping to the top.
    val homeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val libraryListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val current = stack.last()
    val topLevel = tabs.any { it.route == current }

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun selectTab(index: Int) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (index > 0) stack.add(tabs[index].route)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // Silent update check, at most once a day and never on mobile data.
    LaunchedEffect(Unit) {
        if (Net.isMetered(context)) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - Store.lastUpdateCheck < 24L * 60L * 60L * 1000L) return@LaunchedEffect
        Store.lastUpdateCheck = now
        val info = Updater.check() ?: return@LaunchedEffect
        if (info.versionCode <= Updater.currentCode()) return@LaunchedEffect
        if (Store.lastPromptedUpdate >= info.versionCode) return@LaunchedEffect
        Store.lastPromptedUpdate = info.versionCode
        updateInfo = info
    }

    Scaffold(
        // the screens handle their own system-bar insets; the scaffold only reserves the nav bar
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = topLevel,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectTab(index) },
                            icon = {
                                Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
                            },
                            label = { Text(tab.label, maxLines = 1) },
                            // flat, streaming-app style: no pill, white when selected
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(bottom = inner.calculateBottomPadding())) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = current,
                    label = "route",
                    transitionSpec = {
                        when {
                            // the poster flies between screens (shared element), so the page itself only fades
                            targetState is Route.Detail -> fadeIn(tween(260)) togetherWith fadeOut(tween(160))

                            initialState is Route.Detail -> fadeIn(tween(240)) togetherWith fadeOut(tween(180))

                            else -> fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                        }
                    }
                ) { route ->
                    val ctx = SharedCtx(this@SharedTransitionLayout, this)
                    Box(Modifier.fillMaxSize()) {
                        when (route) {
                            is Route.Home -> HomeScreen(
                                refreshKey = refreshKey,
                                listState = homeListState,
                                ctx = ctx,
                                snackbar = snackbar,
                                onOpenDetail = { card, key ->
                                    push(Route.Detail(card.url, card.name, card.poster, card.api, key))
                                },
                                onOpenExtensions = { selectTab(3) },
                                onOpenSwitcher = { showSwitcher = true }
                            )

                            is Route.Library -> LibraryScreen(
                                listState = libraryListState,
                                onOpenDetail = { url, name, poster, api ->
                                    push(Route.Detail(url, name, poster, api, "library|$url"))
                                },
                                onPlayLocal = { file, title ->
                                    push(Route.Player(android.net.Uri.fromFile(file).toString(), null, title, "Downloaded", emptyMap()))
                                }
                            )

                            is Route.Search -> SearchScreen(
                                onOpenDetail = { card, key ->
                                    push(Route.Detail(card.url, card.name, card.poster, card.api, key))
                                },
                                onOpenExtensions = { selectTab(3) },
                                onBack = { selectTab(0) }
                            )

                            is Route.Extensions -> ExtensionsScreen(onBack = {
                                refreshKey++
                                selectTab(0)
                            })

                            is Route.Settings -> SettingsScreen(
                                themeMode = themeMode,
                                onThemeChange = onThemeChange,
                                dynamicColor = dynamicColor,
                                onDynamicColorChange = onDynamicColorChange,
                                onShowUpdate = { updateInfo = it },
                                onBack = { selectTab(0) }
                            )

                            is Route.Detail -> DetailScreen(
                                url = route.url,
                                title = route.name,
                                poster = route.poster,
                                apiName = route.api,
                                sharedKey = route.sharedKey,
                                ctx = ctx,
                                onBack = { pop() },
                                onPlay = { link, title ->
                                    push(
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
                                url = route.url,
                                referer = route.referer,
                                title = route.title,
                                source = route.source,
                                headers = route.headers,
                                onBack = { pop() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSwitcher) {
        ModalBottomSheet(onDismissRequest = { showSwitcher = false }) {
            ExtensionSwitcher(
                onPick = {
                    showSwitcher = false
                    refreshKey++
                },
                onManage = {
                    showSwitcher = false
                    selectTab(3)
                }
            )
        }
    }

    updateInfo?.let { info ->
        UpdateSheet(info) { updateInfo = null }
    }
}

@Composable
private fun ExtensionSwitcher(onPick: () -> Unit, onManage: () -> Unit) {
    val revision by ExtensionManager.revision.collectAsStateWithLifecycle()
    val providers = remember(revision) { Providers.all() }
    var query by remember { mutableStateOf("") }
    val filtered = providers.filter { query.isBlank() || it.name.contains(query, true) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Choose extension", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(
            "The home screen is built from the selected source",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search extensions") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (filtered.isEmpty()) {
            Text(
                "No extension found. Open Extensions to add a repository or import a .cs3 file.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(filtered) { api ->
                ListItem(
                    headlineContent = { Text(api.name) },
                    supportingContent = {
                        Text(api.mainUrl.removePrefix("https://").removePrefix("http://"), maxLines = 1)
                    },
                    leadingContent = { RadioButton(selected = Providers.isActive(api), onClick = null) },
                    trailingContent = { AssistChip(onClick = {}, label = { Text(Languages.chip(api.lang)) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        Providers.select(api)
                        onPick()
                    }
                )
            }
        }

        FilledTonalButton(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)
        ) { Text("Manage extensions") }
    }
}
