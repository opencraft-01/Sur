package com.primebox.ui

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * YouTube-style player: opens inline at the top of the screen (16:9, not fullscreen),
 * with a fullscreen button that rotates into immersive landscape playback.
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var videoBox: FrameLayout
    private lateinit var details: LinearLayout
    private lateinit var fullscreenButton: TextView
    private var fullscreen = false
    private var title: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val referer = intent.getStringExtra("referer")
        @Suppress("UNCHECKED_CAST")
        val headers = (intent.getSerializableExtra("headers") as? HashMap<String, String>) ?: HashMap()
        title = intent.getStringExtra("title") ?: ""

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val context = this
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(context))
        }

        // ------------------------------------------------------------- video
        videoBox = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val playerView = PlayerView(this).apply {
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            setControllerShowTimeoutMs(3000)
        }
        videoBox.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        val videoHeight = (resources.displayMetrics.widthPixels * 9f / 16f).toInt()
        root.addView(videoBox, LinearLayout.LayoutParams(-1, videoHeight))

        // ----------------------------------------------------------- details
        details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 24))
        }
        details.addView(Ui.display(context, title, 17f), LinearLayout.LayoutParams(-1, -2))
        val source = intent.getStringExtra("source") ?: ""
        if (source.isNotBlank()) {
            details.addView(Ui.body(context, source), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = Ui.dp(context, 4)
            })
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fullscreenButton = Ui.tonalButton(context, "⛶  Fullscreen") { toggleFullscreen() }
        actions.addView(fullscreenButton)
        actions.addView(Ui.horizontalSpace(context, 10))
        actions.addView(Ui.tonalButton(context, "⤢  Picture in picture") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) enterPip()
        })
        details.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = Ui.dp(context, 16) })

        details.addView(
            Ui.body(context, "Playback continues in the background while you browse. Use fullscreen for a cinema view."),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(context, 16) }
        )
        root.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        Ui.applyTopInset(details, 0)

        // ------------------------------------------------------------ player
        val requestHeaders = HashMap<String, String>(headers)
        if (!referer.isNullOrBlank()) requestHeaders["Referer"] = referer
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        val context = this
        if (fullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            details.visibility = View.GONE
            videoBox.layoutParams = LinearLayout.LayoutParams(-1, -1)
            hideSystemBars()
            fullscreenButton.text = "⛶  Exit fullscreen"
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            details.visibility = View.VISIBLE
            videoBox.layoutParams = LinearLayout.LayoutParams(
                -1, (resources.displayMetrics.widthPixels * 9f / 16f).toInt()
            )
            showSystemBars()
            fullscreenButton.text = "⛶  Fullscreen"
        }
        videoBox.requestLayout()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val p = player ?: return
        val width = p.videoSize.width
        val height = p.videoSize.height
        val ratio = if (width > 0 && height > 0) Rational(width, height) else Rational(16, 9)
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && p != null && p.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && fullscreen) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
