package com.primebox.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var providers by remember { mutableStateOf(Providers.all()) }
    var installed by remember { mutableStateOf(ExtensionManager.loaded()) }
    var repos by remember { mutableStateOf(Store.repositories) }
    var repoInput by remember { mutableStateOf("") }
    var repoState by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var repoPlugins by remember { mutableStateOf<Map<String, List<ExtensionManager.RepoPlugin>>>(emptyMap()) }
    var diagnostics by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        providers = Providers.all()
        installed = ExtensionManager.loaded()
        repos = Store.repositories
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val (ok, message) = ExtensionManager.importFromUri(context, uri)
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            if (ok) refresh()
        }
    }

    fun loadRepo(url: String) {
        repoState = repoState + (url to "Loading…")
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ExtensionManager.fetchRepo(url) } }
            val info = result.getOrNull()
            if (info == null) {
                repoState = repoState + (url to ("Failed — " + (result.exceptionOrNull()?.message ?: "unknown error")))
            } else {
                repoState = repoState + (url to "${info.name}  •  ${info.plugins.size} extensions")
                repoPlugins = repoPlugins + (url to info.plugins)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 12.dp).clickable { onBack() }.size(22.dp)
                )
                Text("Extensions", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Add a repository, or import a .cs3 file directly",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { runCatching { importer.launch(arrayOf("*/*")) } }) { Text("Import .cs3") }
                OutlinedButton(onClick = {
                    val (count, message) = ExtensionManager.scanDownloads(context)
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    if (count > 0) refresh()
                }) { Text("Scan Download") }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ExtensionManager.loadAll(context) }
                        refresh()
                    }
                }) { Text("Reload") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                diagnostics = ExtensionManager.diagnosticsText(context)
            }) { Text("Diagnostics / load log") }
        }

        item {
            SectionHeader("Active source")
            if (providers.isEmpty()) {
                Text("No extension loaded", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            providers.forEach { api ->
                val active = Store.activeProvider == api.name
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            Providers.select(api)
                            refresh()
                        }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            api.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Badge(Languages.chip(api.lang), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        if (active) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Badge("ACTIVE", MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Installed")
            if (installed.isEmpty()) {
                Text("Nothing installed yet", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            installed.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${if (item.isLocal) "local file" else "downloaded"}  •  v${if (item.version == Int.MIN_VALUE) "?" else item.version}  •  ${item.providers.size} providers",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                ExtensionManager.unload(context, item)
                                refresh()
                            }.padding(8.dp).size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Repositories")
            ExtensionManager.PRESETS.forEach { preset ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("One tap add", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
                            Store.addRepository(preset.url)
                            refresh()
                            loadRepo(preset.url)
                        }) { Text("Add") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    placeholder = { Text("https://example.com/repo.json", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            repoUrl,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reload repository",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { loadRepo(repoUrl) }.padding(8.dp).size(18.dp)
                        )
                        androidx.compose.material3.Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove repository",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                Store.removeRepository(repoUrl)
                                refresh()
                            }.padding(8.dp).size(18.dp)
                        )
                    }
                    repoState[repoUrl]?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    repoPlugins[repoUrl]?.forEach { plugin ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plugin.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    buildString {
                                        append("v${plugin.version}")
                                        if (plugin.authors.isNotEmpty()) append("  •  ${plugin.authors.joinToString(", ")}")
                                        if (plugin.language != null) append("  •  ${plugin.language}")
                                    },
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Button(onClick = {
                                scope.launch {
                                    val (ok, message) = withContext(Dispatchers.IO) {
                                        ExtensionManager.install(context, plugin)
                                    }
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                    if (ok) {
                                        if (Store.activeProvider.isNullOrBlank()) {
                                            Providers.all().firstOrNull()?.let { Providers.select(it) }
                                        }
                                        refresh()
                                    }
                                }
                            }) { Text("Install") }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }

    diagnostics?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { diagnostics = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { diagnostics = null }) { Text("Close") }
            },
            title = { Text("Extension log") },
            text = {
                androidx.compose.foundation.layout.Box(Modifier.height(360.dp)) {
                    LazyColumn {
                        item {
                            Text(text, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        )
    }
}
