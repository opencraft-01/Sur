package com.primebox.compose

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primebox.data.Card
import com.primebox.data.Net
import com.primebox.data.Store
import com.primebox.data.Updater
import com.primebox.ext.Languages
import com.primebox.ext.Providers
import java.io.File

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimeBoxRoot(
    themeMode: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    var refreshKey by remember { mutableStateOf(0) }
    var showSwitcher by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Updater.Info?>(null) }

    val current = stack.last()
    val switcherState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    // silent update check, at most once per day and never on mobile data
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

    if (current is Route.Player) {
        PlayerScreen(
            url = current.url,
            referer = current.referer,
            title = current.title,
            source = current.source,
            headers = current.headers,
            onBack = { stack.removeAt(stack.lastIndex) }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (current is Route.Home || current is Route.Library) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = current is Route.Home,
                        onClick = {
                            if (stack.size > 1) stack.removeRange(1, stack.size) else stack[0] = Route.Home
                        },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = current is Route.Library,
                        onClick = {
                            if (stack.size > 1) stack.removeRange(1, stack.size)
                            stack.add(Route.Library)
                        },
                        icon = { Icon(Icons.Filled.List, contentDescription = "Library") },
                        label = { Text("Library", fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (current is Route.Home) {
                FloatingActionButton(
                    onClick = { showSwitcher = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("⧉", fontSize = 20.sp)
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (current) {
                is Route.Home -> HomeScreen(
                    refreshKey = refreshKey,
                    onOpenDetail = { card ->
                        stack.add(Route.Detail(card.url, card.name, card.poster, card.api))
                    },
                    onOpenSearch = { stack.add(Route.Search) },
                    onOpenExtensions = { stack.add(Route.Extensions) },
                    onOpenSettings = { stack.add(Route.Settings) },
                    onOpenSwitcher = { showSwitcher = true }
                )

                is Route.Library -> LibraryScreen(
                    onOpenDetail = { url, name, poster, api ->
                        stack.add(Route.Detail(url, name, poster, api))
                    },
                    onPlayLocal = { file, title ->
                        stack.add(Route.Player(file.absolutePath, null, title, "Downloaded", emptyMap()))
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

                is Route.Detail -> DetailScreen(
                    url = current.url,
                    title = current.name,
                    poster = current.poster,
                    apiName = current.api,
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

                is Route.Player -> Unit
            }
        }
    }

    if (showSwitcher) {
        ModalBottomSheet(
            onDismissRequest = { showSwitcher = false },
            sheetState = switcherState,
            containerColor = MaterialTheme.colorScheme.surface
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

@Composable
private fun ExtensionSwitcher(onPick: () -> Unit, onManage: () -> Unit) {
    val providers = remember { Providers.all() }
    var query by remember { mutableStateOf("") }
    val filtered = providers.filter { query.isBlank() || it.name.contains(query, true) }
    val active = Store.activeProvider

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Choose extension", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "The home screen is built from the selected source",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedTextField(
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
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        filtered.take(20).forEach { api ->
            Surface(
                color = if (api.name == active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .androidxClickable {
                        Providers.select(api)
                        onPick()
                    }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(api.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            api.mainUrl.removePrefix("https://").removePrefix("http://"),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Badge(Languages.chip(api.lang), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Button(
            onClick = onManage,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Manage extensions") }
        Spacer(Modifier.height(18.dp))
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
