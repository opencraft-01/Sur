@file:OptIn(ExperimentalMaterial3Api::class)

package com.primebox.compose

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.primebox.data.Downloader
import com.primebox.data.Store
import java.io.File

@Composable
fun LibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    onOpenDetail: (url: String, name: String, poster: String?, api: String?) -> Unit,
    onPlayLocal: (File, String) -> Unit
) {
    val context = LocalContext.current
    val downloads by Downloader.items.collectAsStateWithLifecycle()
    var history by remember { mutableStateOf(Store.history()) }
    var tab by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Downloader.Item?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                if (tab == 1 && history.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            }
        )
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Downloads (${downloads.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History (${history.size})") })
        }

        if (tab == 0) {
            if (downloads.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Download,
                    title = "Nothing downloaded yet",
                    body = "Open a title, load its sources and tap the download icon."
                )
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onClick = {
                                val path = item.filePath
                                if (item.state == Downloader.State.DONE && path != null && File(path).exists()) {
                                    onPlayLocal(File(path), item.title)
                                } else if (item.state == Downloader.State.FAILED) {
                                    Downloader.start(context, item)
                                }
                            },
                            onDelete = { pendingDelete = item }
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "No history",
                    body = "Watch something and it shows up here."
                )
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(history) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOf(
                                        entry.provider,
                                        DateUtils.getRelativeTimeSpanString(entry.time).toString()
                                    ).filter { it.isNotBlank() }.joinToString("  •  ")
                                )
                            },
                            leadingContent = { Thumb(entry.poster, entry.title) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onOpenDetail(entry.url, entry.title, entry.poster, entry.provider)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = { Text("“${item.title}” will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    Downloader.delete(item)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearHistory()
                    history = Store.history()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Thumb(poster: String?, title: String) {
    PosterArt(
        poster, title,
        Modifier.size(width = 52.dp, height = 78.dp).clip(MaterialTheme.shapes.small),
        showTitleFallback = false
    )
}

@Composable
private fun DownloadRow(item: Downloader.Item, onClick: () -> Unit, onDelete: () -> Unit) {
    val running = item.state == Downloader.State.RUNNING || item.state == Downloader.State.QUEUED
    val unknown = item.indeterminate || item.totalBytes <= 0
    Column {
        ListItem(
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text(
                        when (item.state) {
                            Downloader.State.DONE -> buildString {
                                append("Ready to play")
                                if (item.quality > 0) append("  •  ${item.quality}p")
                                val size = item.filePath?.let { File(it).length() } ?: 0L
                                if (size > 0) append("  •  ${bytesLabel(size)}")
                            }
                            Downloader.State.RUNNING ->
                                if (unknown && item.type != "M3U8") "Downloading…" else "Downloading… ${item.progress}%"
                            Downloader.State.FAILED -> "Failed — tap to retry" + (item.error?.let { " ($it)" } ?: "")
                            Downloader.State.QUEUED -> "Queued"
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (running) {
                        WavyProgress(
                            progress = if (item.state == Downloader.State.QUEUED || (unknown && item.type != "M3U8")) null
                            else item.progress / 100f,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            buildString {
                                append(bytesLabel(item.bytes))
                                if (item.totalBytes > 0) append(" / ${bytesLabel(item.totalBytes)}")
                                append("  •  ")
                                append(if (item.speed > 0) bytesLabel(item.speed) + "/s" else "—")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            leadingContent = { Thumb(item.poster, item.title) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.state == Downloader.State.FAILED) {
                        IconButton(onClick = onClick) { Icon(Icons.Filled.Refresh, contentDescription = "Retry") }
                    }
                    if (item.state == Downloader.State.DONE) {
                        IconButton(onClick = onClick) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play download", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = if (running) "Cancel download" else "Delete download")
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
