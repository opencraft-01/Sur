package com.primebox.compose

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

private const val UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/**
 * Inline player like YouTube: video at the top (16:9), details below, and a
 * fullscreen toggle that rotates into immersive landscape playback.
 */
@Composable
fun PlayerScreen(
    url: String,
    referer: String?,
    title: String,
    source: String,
    headers: Map<String, String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    val player = remember(url) {
        val requestHeaders = HashMap(headers)
        if (!referer.isNullOrBlank()) requestHeaders["Referer"] = referer
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(url) {
        onDispose { player.release() }
    }

    DisposableEffect(fullscreen) {
        val activity = context as? Activity
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (fullscreen) 0.dp else 220.dp)
        ) {
            if (!fullscreen) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (fullscreen) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "←",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 12.dp).androidxClickable { onBack() }
                    )
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                }
                if (source.isNotBlank()) {
                    Text(source, color = Color(0xFFC9CDD6), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = { fullscreen = true }) { Text("⛶  Fullscreen") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Playback keeps running while you browse. Use fullscreen for a cinema view.",
                    color = Color(0xFF9AA0A6),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
