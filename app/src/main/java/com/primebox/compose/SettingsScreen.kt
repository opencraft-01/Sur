@file:OptIn(ExperimentalMaterial3Api::class)

package com.primebox.compose

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.Coil
import com.primebox.BuildConfig
import com.primebox.data.HomeCache
import com.primebox.data.Store
import com.primebox.data.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onShowUpdate: (Updater.Info) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var checking by remember { mutableStateOf(false) }
    var dataSaver by remember { mutableStateOf(Store.dataSaver) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = inner) {
            item { Header("Appearance") }
            item {
                val options = listOf("System" to 0, "Light" to 1, "Dark" to 2)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            selected = themeMode == value,
                            onClick = { onThemeChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text("Dynamic colour") },
                        supportingContent = { Text("Match the colours of your wallpaper (Material You)") },
                        trailingContent = { Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange) },
                        colors = transparent,
                        modifier = Modifier.clickable { onDynamicColorChange(!dynamicColor) }
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("Data") }
            item {
                ListItem(
                    headlineContent = { Text("Data saver") },
                    supportingContent = { Text("Skip update checks and prefetching on mobile data") },
                    trailingContent = {
                        Switch(checked = dataSaver, onCheckedChange = { dataSaver = it; Store.dataSaver = it })
                    },
                    colors = transparent,
                    modifier = Modifier.clickable { dataSaver = !dataSaver; Store.dataSaver = dataSaver }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear image cache") },
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable {
                        // posters are loaded by Coil — that is the cache to clear
                        val loader = Coil.imageLoader(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        say("Image cache cleared")
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear cached home") },
                    leadingContent = { Icon(Icons.Filled.CleaningServices, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable {
                        HomeCache.clear(context)
                        say("Home cache cleared")
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Clear history") },
                    supportingContent = { Text("Watch history and recent searches") },
                    leadingContent = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    colors = transparent,
                    modifier = Modifier.clickable {
                        Store.clearHistory()
                        Store.clearRecentSearches()
                        say("History cleared")
                    }
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("Updates") }
            item {
                ListItem(
                    headlineContent = { Text("Check for updates") },
                    supportingContent = { Text("Installed: ${Updater.currentName()} (build ${Updater.currentCode()})") },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                    trailingContent = {
                        if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    },
                    colors = transparent,
                    modifier = Modifier.clickable(enabled = !checking) {
                        scope.launch {
                            checking = true
                            val info = Updater.check()
                            checking = false
                            when {
                                info == null -> say("No release found")
                                info.versionCode <= Updater.currentCode() -> say("You're up to date")
                                else -> onShowUpdate(info)
                            }
                        }
                    }
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Header("About") }
            item {
                ListItem(
                    headlineContent = { Text("PrimeBox ${BuildConfig.VERSION_NAME}") },
                    supportingContent = {
                        Text("Extension-driven media centre. Extensions are third-party code — install only repositories you trust.")
                    },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    colors = transparent
                )
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
