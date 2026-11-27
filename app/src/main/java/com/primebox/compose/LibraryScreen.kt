package com.primebox.compose

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.primebox.data.Downloader
import com.primebox.data.Store
import java.io.File

@Composable
fun LibraryScreen(
    onOpenDetail: (String, String, String?, String?) -> Unit,
    onPlayLocal: (File, String) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0 downloads, 1 history
    var downloads by remember { mutableStateOf(Downloader.all()) }
    var history by remember { mutableStateOf(Store.history()) }

    DisposableEffect(Unit) {
        Downloader.listen {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                downloads = Downloader.all()
            }
        }
        onDispose { Downloader.listen(null) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            "Library",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp)
        )
        Text(
            "Your downloads and watch history",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 4.dp)
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Downloads", downloads.size.toString(), mode == 0, Modifier.weight(1f)) { mode = 0 }
            StatCard("History", history.size.toString(), mode == 1, Modifier.weight(1f)) { mode = 1 }
        }

        if (mode == 0) {
            if (downloads.isEmpty()) {
                EmptyState("Nothing downloaded yet", "Open a title, load its sources and tap the download icon.")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    items(downloads) { item ->
                        DownloadRow(item,
                            onPlay = {
                                val path = item.filePath
                                if (item.state == Downloader.State.DONE && path != null && File(path).exists()) {
                                    onPlayLocal(File(path), item.title)
                                } else if (item.state == Downloader.State.FAILED) {
                                    Downloader.start(context, item)
                                }
                            },
                            onDelete = {
                                Downloader.delete(item)
                                downloads = Downloader.all()
                            }
                        )
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                EmptyState("No history", "Watch something and it shows up here.")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    items(history) { entry ->
                        HistoryRow(entry) {
                            onOpenDetail(entry.url, entry.title, entry.poster, entry.provider)
                        }
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.OutlinedButton(onClick = {
                            Store.clearHistory()
                            history = Store.history()
                        }) { Text("Clear history") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DownloadRow(item: Downloader.Item, onPlay: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onPlay() }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp, 74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (!item.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val running = item.state == Downloader.State.RUNNING
                Text(
                    when (item.state) {
                        Downloader.State.DONE -> "Downloaded  •  ${item.quality}p"
                        Downloader.State.RUNNING -> "Downloading… ${item.progress}%"
                        Downloader.State.FAILED -> "Failed — tap to retry"
                        else -> "Queued"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (running) {
                    WavyProgress(item.progress / 100f, Modifier.padding(top = 6.dp))
                    Text(
                        "${item.progress}%   •   ${bytesLabel(item.bytes)} / ${bytesLabel(item.totalBytes)}   •   ${if (item.speed > 0) bytesLabel(item.speed) + "/s" else "—"}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
            }
            androidx.compose.material3.Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onDelete() }.padding(8.dp).size(20.dp)
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: Store.HistoryEntry, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onClick() }
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp, 74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (!entry.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.poster,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    entry.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(entry.provider, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
