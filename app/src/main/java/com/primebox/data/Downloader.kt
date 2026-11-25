package com.primebox.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Small download engine: progressive MP4 and HLS (segments are concatenated
 * into a single .ts file so it plays offline without a playlist).
 */
object Downloader {

    private const val TAG = "PrimeBoxDownload"
    private const val STORE_KEY = "downloads_v1"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    enum class State { QUEUED, RUNNING, DONE, FAILED }

    data class Item(
        val id: String,
        val title: String,
        val poster: String?,
        val url: String,
        val referer: String?,
        val headers: Map<String, String>,
        val type: String,
        val quality: Int,
        val provider: String,
        var state: State = State.QUEUED,
        var progress: Int = 0,
        var filePath: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val items = LinkedHashMap<String, Item>()
    private var listener: (() -> Unit)? = null

    fun listen(block: (() -> Unit)?) {
        listener = block
    }

    fun all(): List<Item> = items.values.toList()

    fun init(context: Context) {
        if (items.isNotEmpty()) return
        val raw = Store.getRaw(STORE_KEY) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val headers = HashMap<String, String>()
                o.optJSONObject("headers")?.let { h ->
                    h.keys().forEach { key -> headers[key] = h.optString(key) }
                }
                val item = Item(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    poster = if (o.has("poster")) o.optString("poster") else null,
                    url = o.optString("url"),
                    referer = if (o.has("referer")) o.optString("referer") else null,
                    headers = headers,
                    type = o.optString("type", "VIDEO"),
                    quality = o.optInt("quality", 0),
                    provider = o.optString("provider"),
                    state = if (o.optString("state") == "DONE") State.DONE else State.QUEUED,
                    progress = o.optInt("progress", 0),
                    filePath = if (o.has("filePath")) o.optString("filePath") else null
                )
                if (item.state == State.DONE && item.filePath != null && !File(item.filePath).exists()) {
                    item.state = State.FAILED
                }
                items[item.id] = item
            }
        }
        Log.i(TAG, "restored ${items.size} downloads")
    }

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }

    fun start(context: Context, item: Item) {
        val existing = items[item.id]
        if (existing != null && (existing.state == State.RUNNING || existing.state == State.DONE)) return
        items[item.id] = item
        item.state = State.QUEUED
        persist()
        notifyChanged()

        scope.launch {
            item.state = State.RUNNING
            item.progress = 0
            notifyChanged()
            val target = File(dir(context), "${item.id}.${if (item.type == "M3U8") "ts" else "mp4"}")
            try {
                if (item.type == "M3U8") {
                    downloadHls(item, target)
                } else {
                    downloadDirect(item, target)
                }
                item.filePath = target.absolutePath
                item.state = State.DONE
                item.progress = 100
                Log.i(TAG, "downloaded ${item.title} -> ${target.absolutePath}")
            } catch (t: Throwable) {
                Log.e(TAG, "download failed: ${item.title}", t)
                item.state = State.FAILED
                runCatching { target.delete() }
            }
            persist()
            notifyChanged()
        }
    }

    fun delete(item: Item) {
        item.filePath?.let { path -> runCatching { File(path).delete() } }
        items.remove(item.id)
        persist()
        notifyChanged()
    }

    fun isDownloaded(url: String): Boolean =
        items.values.any { it.url == url && it.state == State.DONE }

    // ------------------------------------------------------------ downloaders

    private fun request(url: String, item: Item, rangeStart: Long? = null): Request {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (rangeStart != null) builder.header("Range", "bytes=$rangeStart-")
        if (!item.referer.isNullOrBlank()) builder.header("Referer", item.referer)
        item.headers.forEach { (k, v) -> if (k.lowercase() != "range") builder.header(k, v) }
        return builder.build()
    }

    private fun downloadDirect(item: Item, target: File) {
        client.newCall(request(item.url, item)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastTick = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val pct = ((written * 100) / total).toInt().coerceIn(0, 99)
                            if (pct != item.progress && System.currentTimeMillis() - lastTick > 350) {
                                item.progress = pct
                                lastTick = System.currentTimeMillis()
                                notifyChanged()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun downloadHls(item: Item, target: File) {
        val playlistUrl = item.url
        var playlist = fetchText(item, playlistUrl)

        // master playlist -> pick the highest bandwidth variant
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val lines = playlist.lines()
            var bestUrl: String? = null
            var bestBandwidth = -1L
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                    val next = lines.getOrNull(i + 1)?.trim()
                    if (!next.isNullOrBlank() && !next.startsWith("#") && bandwidth >= bestBandwidth) {
                        bestBandwidth = bandwidth
                        bestUrl = resolve(playlistUrl, next)
                    }
                }
            }
            val variant = bestUrl ?: error("no variant playlist")
            playlist = fetchText(item, variant)
            return downloadSegments(item, target, playlist, variant)
        }
        downloadSegments(item, target, playlist, playlistUrl)
    }

    private fun downloadSegments(item: Item, target: File, playlist: String, playlistUrl: String) {
        val segments = playlist.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(playlistUrl, it) }
        if (segments.isEmpty()) error("playlist has no segments")

        target.outputStream().use { output ->
            segments.forEachIndexed { index, segment ->
                client.newCall(request(segment, item)).execute().use { response ->
                    if (!response.isSuccessful) error("segment HTTP ${response.code}")
                    val body = response.body ?: return@use
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                item.progress = (((index + 1) * 100) / segments.size).coerceIn(0, 99)
                notifyChanged()
            }
        }
    }

    private fun fetchText(item: Item, url: String): String =
        client.newCall(request(url, item)).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty playlist")
        }

    private fun resolve(base: String, relative: String): String {
        return try {
            java.net.URI(base).resolve(relative).toString()
        } catch (t: Throwable) {
            relative
        }
    }

    // -------------------------------------------------------------- plumbing

    private fun notifyChanged() {
        runCatching { listener?.invoke() }
    }

    private fun persist() {
        val array = JSONArray()
        items.values.forEach { item ->
            val headers = JSONObject()
            item.headers.forEach { (k, v) -> headers.put(k, v) }
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("poster", item.poster ?: "")
                    .put("url", item.url)
                    .put("referer", item.referer ?: "")
                    .put("headers", headers)
                    .put("type", item.type)
                    .put("quality", item.quality)
                    .put("provider", item.provider)
                    .put("state", item.state.name)
                    .put("progress", item.progress)
                    .put("filePath", item.filePath ?: "")
            )
        }
        Store.putRaw(STORE_KEY, array.toString())
    }
}
