package com.primebox.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primebox.data.Updater
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Bottom popup for in-app updates: shows the changelog, a smooth wavy progress
 * line with speed / percent / MB, and opens the installer automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    info: Updater.Info,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var percent by remember { mutableStateOf(0) }
    var downloaded by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var speed by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun start() {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            val file = Updater.download(context, info) { p ->
                percent = p.percent
                downloaded = p.downloaded
                total = p.total
                speed = p.bytesPerSecond
            }
            if (file == null) {
                busy = false
                message = "Download failed — check your connection"
                return@launch
            }
            percent = 100
            message = "Opening installer…"
            Updater.install(context, file)
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text(
                "Update available",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "PrimeBox ${info.versionName}  •  you have ${Updater.currentName()}",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (info.notes.isNotBlank()) {
                Text(
                    info.notes.trim(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            WavyProgress(percent / 100f)
            Text(
                String.format(
                    Locale.US,
                    "%d%%   •   %s / %s   •   %s",
                    percent,
                    bytesLabel(downloaded),
                    bytesLabel(total),
                    if (speed > 0) bytesLabel(speed) + "/s" else "—"
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { start() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Downloading…" else "Update now")
            }
            if (message.isNotBlank()) {
                Text(
                    message,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Text(
                "The APK downloads inside the app and the installer opens automatically.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
            )
        }
    }
}
