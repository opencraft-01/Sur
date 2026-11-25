package com.primebox.img

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Tiny dependency-free image loader (keeps the APK small — no Coil/Glide).
 */
object ImageLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(80) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun load(url: String?, view: ImageView, headers: Map<String, String>? = null, targetPx: Int = 480) {
        if (url.isNullOrBlank()) {
            view.tag = null
            view.setImageDrawable(null)
            return
        }
        view.tag = url
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageDrawable(null)
        scope.launch {
            val bitmap = runCatching { fetch(url, headers, targetPx) }.getOrNull() ?: return@launch
            cache.put(url, bitmap)
            withContext(Dispatchers.Main) {
                if (view.tag == url) view.setImageBitmap(bitmap)
            }
        }
    }

    private fun fetch(url: String, headers: Map<String, String>?, targetPx: Int): Bitmap? {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers?.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
}
