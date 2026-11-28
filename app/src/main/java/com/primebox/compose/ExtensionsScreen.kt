@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.primebox.compose

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primebox.data.Store
import com.primebox.ext.ExtensionManager
import com.primebox.ext.Languages
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtensionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val restartRequired by ExtensionManager.restartRequired.collectAsStateWithLifecycle()

    var providers by remember { mutableStateOf(Providers.all()) }
    var installed by remember { mutableStateOf(ExtensionManager.loaded()) }
    var repos by remember { mutableStateOf(Store.repositories) }
    var activeName by remember { mutableStateOf(Store.activeProvider) }
    var repoInput by remember { mutableStateOf("") }
    var repoState by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var repoPlugins by remember { mutableStateOf<Map<String, List<ExtensionManager.RepoPlugin>>>(emptyMap()) }
    var installing by remember { mutableStateOf<Set<String>>(emptySet()) }
    var diagnostics by remember { mutableStateOf<String?>(null) }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun refresh() {
        providers = Providers.all()
        installed = ExtensionManager.loaded()
        repos = Store.repositories
        activeName = Store.activeProvider
    }

    // The drop-in folder lives on shared storage; Android 11+ needs "all files access" for it.
    val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val (ok, message) = withContext(Dispatchers.IO) { ExtensionManager.importFromUri(context, uri) }
                say(message)
                if (ok) refresh()
            }
        }
    }

    fun loadRepo(url: String) {
        repoState = repoState + (url to "Loading…")
        scope.launch {
            val result = runCatching { ExtensionManager.fetchRepo(url) }
            val info = result.getOrNull()
            if (info == null) {
                repoState = repoState + (url to ("Failed — " + (result.exceptionOrNull()?.message ?: "unknown error")))
            } else {
                repoState = repoState + (url to "${info.name}  •  ${info.plugins.size} extensions")
                repoPlugins = repoPlugins + (url to info.plugins)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Add a repository, or import a .cs3 file directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (restartRequired) {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Restart PrimeBox to finish removing or updating an extension — its old code stays loaded until then.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            if (needsAllFiles) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Allow file access", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Needed to load extensions dropped into /Cloudstream3/plugins or Download. Importing with the file picker works without it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            FilledTonalButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }) { Text("Open settings") }
                        }
                    }
                }
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { runCatching { importer.launch(arrayOf("*/*")) } },
                        label = { Text("Import .cs3") },
                        leadingIcon = { Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                val (count, message) = withContext(Dispatchers.IO) { ExtensionManager.scanDownloads(context) }
                                say(message)
                                if (count > 0) refresh()
                            }
                        },
                        label = { Text("Scan Download") },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { ExtensionManager.loadAll(context) }
                                refresh()
                                say("Reloaded")
                            }
                        },
                        label = { Text("Reload") },
                        leadingIcon = { Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { diagnostics = ExtensionManager.diagnosticsText(context) },
                        label = { Text("Load log") },
                        leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            item { SectionLabel("Active source") }
            if (providers.isEmpty()) {
                item { Muted("No extension loaded") }
            }
            items(providers) { api ->
                ListItem(
                    headlineContent = { Text(api.name) },
                    supportingContent = { Text(Languages.chip(api.lang)) },
                    leadingContent = { RadioButton(selected = activeName == api.name && Providers.isActive(api), onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        Providers.select(api)
                        refresh()
                    }
                )
            }

            item { SectionLabel("Installed") }
            if (installed.isEmpty()) {
                item { Muted("Nothing installed yet") }
            }
            items(installed) { item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    supportingContent = {
                        Text(
                            "${if (item.isLocal) "local file" else "downloaded"}  •  " +
                                "v${if (item.version == Int.MIN_VALUE) "?" else item.version}  •  ${item.providers.size} providers"
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            ExtensionManager.unload(context, item)
                            refresh()
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.name}") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            item { SectionLabel("Repositories") }
            items(ExtensionManager.PRESETS) { preset ->
                ListItem(
                    headlineContent = { Text(preset.name) },
                    supportingContent = { Text("One tap add") },
                    trailingContent = {
                        Button(onClick = {
                            Store.addRepository(preset.url)
                            refresh()
                            loadRepo(preset.url)
                        }) { Text("Add") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://example.com/repo.json") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        val url = repoInput.trim()
                        if (url.isNotBlank()) {
                            Store.addRepository(url)
                            repoInput = ""
                            refresh()
                            loadRepo(url)
                        }
                    }) { Text("Add") }
                }
            }

            items(repos) { repoUrl ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                repoUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { loadRepo(repoUrl) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload repository")
                            }
                            IconButton(onClick = {
                                Store.removeRepository(repoUrl)
                                refresh()
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove repository") }
                        }
                        repoState[repoUrl]?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        repoPlugins[repoUrl]?.forEach { plugin ->
                            HorizontalDivider(Modifier.padding(top = 8.dp, end = 12.dp))
                            Row(Modifier.padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        buildString {
                                            append("v${plugin.version}")
                                            if (plugin.authors.isNotEmpty()) append("  •  ${plugin.authors.joinToString(", ")}")
                                            if (plugin.language != null) append("  •  ${plugin.language}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val busy = plugin.url in installing
                                FilledTonalButton(
                                    enabled = !busy,
                                    onClick = {
                                        installing = installing + plugin.url
                                        scope.launch {
                                            val (ok, message) = ExtensionManager.install(context, plugin)
                                            installing = installing - plugin.url
                                            say(message)
                                            if (ok) {
                                                if (Store.activeProvider.isNullOrBlank()) {
                                                    Providers.all().firstOrNull()?.let { Providers.select(it) }
                                                }
                                                refresh()
                                            }
                                        }
                                    }
                                ) {
                                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Text("Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    diagnostics?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            confirmButton = { TextButton(onClick = { diagnostics = null }) { Text("Close") } },
            title = { Text("Extension log") },
            text = {
                Box(Modifier.height(360.dp)) {
                    SelectionContainer {
                        LazyColumn {
                            item { Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
