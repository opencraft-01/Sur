package com.primebox.img

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Small image loader with memory + disk cache, request limiting and a
 * placeholder (shimmer) that hides itself once the bitmap is ready.
 */
object ImageLoader {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val memory = object : LruCache<String, Bitmap>(160) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gate = Semaphore(6)
    private val main = Handler(Looper.getMainLooper())
    private var diskDir: File? = null

    fun init(context: Context) {
        if (diskDir == null) {
            diskDir = File(context.cacheDir, "img").apply { mkdirs() }
        }
    }

    fun load(
        url: String?,
        view: ImageView,
        headers: Map<String, String>? = null,
        targetPx: Int = 480,
        placeholder: View? = null
    ) {
        val fixed = normalize(url)
        if (fixed == null) {
            placeholder?.visibility = View.GONE
            view.setImageDrawable(null)
            return
        }
        view.tag = fixed

        memory.get(fixed)?.let { bitmap ->
            view.setImageBitmap(bitmap)
            placeholder?.visibility = View.GONE
            return
        }

        val cached = readDisk(fixed)
        if (cached != null) {
            memory.put(fixed, cached)
            view.setImageBitmap(cached)
            placeholder?.visibility = View.GONE
            return
        }

        view.setImageDrawable(null)
        placeholder?.visibility = View.VISIBLE

        scope.launch {
            gate.withPermit {
                val bitmap = runCatching { fetch(fixed, headers, targetPx) }.getOrNull()
                if (bitmap != null) {
                    memory.put(fixed, bitmap)
                    writeDisk(fixed, bitmap)
                }
                main.post {
                    if (view.tag == fixed) {
                        if (bitmap != null) {
                            view.setImageBitmap(bitmap)
                            placeholder?.visibility = View.GONE
                        } else {
                            placeholder?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    /** Warm the cache without a view. */
    fun prefetch(url: String?, headers: Map<String, String>? = null, targetPx: Int = 400) {
        val fixed = normalize(url) ?: return
        if (memory.get(fixed) != null || readDisk(fixed) != null) return
        scope.launch {
            gate.withPermit {
                val bitmap = runCatching { fetch(fixed, headers, targetPx) }.getOrNull() ?: return@withPermit
                memory.put(fixed, bitmap)
                writeDisk(fixed, bitmap)
            }
        }
    }

    private fun normalize(url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> "https://$value"
        }
    }

    private fun key(url: String): String =
        Integer.toHexString(url.hashCode()) + "_" + url.length

    private fun readDisk(url: String): Bitmap? {
        val file = File(diskDir ?: return null, key(url))
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun writeDisk(url: String, bitmap: Bitmap) {
        val dir = diskDir ?: return
        runCatching {
            File(dir, key(url)).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        }
    }

    private fun fetch(url: String, headers: Map<String, String>?, targetPx: Int): Bitmap? {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        headers?.forEach { (k, v) -> if (k.isNotBlank()) builder.header(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
