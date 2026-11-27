package com.primebox.compose

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primebox.BuildConfig
import com.primebox.data.HomeCache
import com.primebox.data.Store
import com.primebox.data.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    onShowUpdate: (Updater.Info) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf("") }
    var dataSaver by remember { mutableStateOf(Store.dataSaver) }
    var autoplay by remember { mutableStateOf(Store.autoplayNext) }

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
                Text("Settings", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        item {
            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, value) ->
                    val selected = themeMode == value
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).clickable { onThemeChange(value) }
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Playback")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Autoplay next episode", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = autoplay, onCheckedChange = { autoplay = it; Store.autoplayNext = it })
            }
        }

        item {
            SectionHeader("Data")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Data saver (skip prefetch on mobile data)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = dataSaver, onCheckedChange = { dataSaver = it; Store.dataSaver = it })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                com.primebox.img.ImageLoader.clearCache()
                android.widget.Toast.makeText(context, "Image cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Clear image cache") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                HomeCache.clear(context)
                android.widget.Toast.makeText(context, "Home cache cleared", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Clear cached home") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                Store.clearHistory()
                Store.clearRecentSearches()
                android.widget.Toast.makeText(context, "History cleared", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Clear history") }
        }

        item {
            SectionHeader("Updates")
            Text(
                "Installed: ${Updater.currentName()} (build ${Updater.currentCode()})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                scope.launch {
                    updateStatus = "Checking…"
                    val info = Updater.check()
                    if (info == null) {
                        updateStatus = "No release found"
                    } else if (info.versionCode <= Updater.currentCode()) {
                        updateStatus = "Up to date"
                    } else {
                        updateStatus = "Update ${info.versionName} available"
                        onShowUpdate(info)
                    }
                }
            }) { Text("Check for updates") }
            if (updateStatus.isNotBlank()) {
                Text(updateStatus, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item {
            SectionHeader("About")
            Text(
                "PrimeBox ${BuildConfig.VERSION_NAME}\nExtension-driven media centre. Extensions are third-party code — install only repositories you trust.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}
