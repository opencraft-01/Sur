package com.primebox.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Small download engine: progressive MP4 and HLS (segments are concatenated
 * into a single .ts file so it plays offline without a playlist).
 *
 * State lives in an immutable [StateFlow] so the UI recomposes on every change.
 * (The previous version mutated shared objects from several threads, which both
 * risked ConcurrentModificationException and meant Compose never saw progress.)
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
        val state: State = State.QUEUED,
        val progress: Int = 0,
        val filePath: String? = null,
        val bytes: Long = 0L,
        val totalBytes: Long = 0L,
        val speed: Long = 0L,
        val indeterminate: Boolean = false,
        val error: String? = null
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()

    private val _items = MutableStateFlow<List<Item>>(emptyList())

    /** Newest state of every download, in the order they were started. */
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    @Volatile
    private var restored = false

    fun all(): List<Item> = _items.value

    private fun find(id: String): Item? = _items.value.firstOrNull { it.id == id }

    private fun update(id: String, change: (Item) -> Item) {
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    @Synchronized
    fun init(context: Context) {
        if (restored) return
        restored = true
        val raw = Store.getRaw(STORE_KEY) ?: return
        runCatching {
            val array = JSONArray(raw)
            val restoredItems = ArrayList<Item>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val headers = HashMap<String, String>()
                o.optJSONObject("headers")?.let { h ->
                    h.keys().forEach { key -> headers[key] = h.optString(key) }
                }
                val path = o.optString("filePath").ifBlank { null }
                val done = o.optString("state") == "DONE" && path != null && File(path).exists()
                restoredItems += Item(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    poster = o.optString("poster").ifBlank { null },
                    url = o.optString("url"),
                    referer = o.optString("referer").ifBlank { null },
                    headers = headers,
                    type = o.optString("type", "VIDEO"),
                    quality = o.optInt("quality", 0),
                    provider = o.optString("provider"),
                    // anything that was still queued / running when the app died is
                    // shown as failed, so "tap to retry" (with resume) is available
                    state = if (done) State.DONE else State.FAILED,
                    progress = if (done) 100 else o.optInt("progress", 0),
                    filePath = if (done) path else null
                )
            }
            _items.value = restoredItems
        }
        Log.i(TAG, "restored ${_items.value.size} downloads")
    }

    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "downloads").apply { mkdirs() }
    }

    fun start(context: Context, item: Item) {
        val appContext = context.applicationContext
        val existing = find(item.id)
        if (existing != null && existing.state != State.FAILED) return

        val fresh = item.copy(
            state = State.QUEUED, progress = 0, filePath = null,
            bytes = 0L, totalBytes = 0L, speed = 0L, indeterminate = false, error = null
        )
        _items.update { list ->
            if (existing == null) list + fresh else list.map { if (it.id == fresh.id) fresh else it }
        }
        persist()
        jobs[fresh.id] = scope.launch { run(appContext, fresh) }
    }

    fun delete(item: Item) {
        // stop the transfer first so it cannot recreate the file or re-add the entry
        jobs.remove(item.id)?.cancel()
        calls.remove(item.id)?.cancel()
        val path = find(item.id)?.filePath ?: item.filePath
        _items.update { list -> list.filterNot { it.id == item.id } }
        path?.let { runCatching { File(it).delete() } }
        persist()
    }

    fun isDownloaded(url: String): Boolean =
        _items.value.any { it.url == url && it.state == State.DONE }

    // ------------------------------------------------------------------ runner

    private fun run(context: Context, item: Item) {
        val target = File(dir(context), "${item.id}.${if (item.type == "M3U8") "ts" else "mp4"}")
        update(item.id) { it.copy(state = State.RUNNING, progress = 0) }
        try {
            if (item.type == "M3U8") downloadHls(item, target) else downloadDirect(item, target)
            if (!target.exists() || target.length() < 1024L) {
                throw IllegalStateException("empty download (0 bytes)")
            }
            update(item.id) {
                it.copy(
                    state = State.DONE, progress = 100, filePath = target.absolutePath,
                    bytes = target.length(), totalBytes = target.length(),
                    speed = 0L, indeterminate = false, error = null
                )
            }
            Log.i(TAG, "downloaded ${item.title} -> ${target.absolutePath}")
        } catch (t: Throwable) {
            if (find(item.id) == null) {
                // deleted while running
                runCatching { target.delete() }
            } else {
                Log.e(TAG, "download failed: ${item.title}", t)
                // the partial file is kept on purpose: a retry resumes from it
                update(item.id) { it.copy(state = State.FAILED, speed = 0L, error = t.message) }
            }
        } finally {
            calls.remove(item.id)
            jobs.remove(item.id)
        }
        persist()
    }

    // ------------------------------------------------------------ downloaders

    private fun request(url: String, item: Item, rangeStart: Long? = null): Request {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (rangeStart != null) builder.header("Range", "bytes=$rangeStart-")
        if (!item.referer.isNullOrBlank()) builder.header("Referer", item.referer)
        item.headers.forEach { (k, v) -> if (k.lowercase() != "range") builder.header(k, v) }
        return builder.build()
    }

    private fun newCall(item: Item, request: Request): Call =
        client.newCall(request).also { calls[item.id] = it }

    /**
     * Progressive download with resume: if the transfer drops, the next attempt
     * continues from the bytes already on disk (HTTP Range) instead of starting over.
     */
    private fun downloadDirect(item: Item, target: File) {
        var attempts = 0
        var lastError: Throwable? = null
        while (attempts < 3) {
            if (find(item.id) == null) throw IllegalStateException("cancelled")
            val resumeFrom = if (target.exists()) target.length() else 0L
            try {
                newCall(item, request(item.url, item, if (resumeFrom > 0) resumeFrom else null)).execute().use { response ->
                    if (response.code == 416) {
                        // server rejected the range: start clean
                        target.delete()
                        throw IllegalStateException("range rejected")
                    }
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val appending = response.code == 206 && resumeFrom > 0
                    val base = if (appending) resumeFrom else 0L
                    val body = response.body ?: throw IllegalStateException("empty body")
                    val length = body.contentLength()
                    val total = if (length <= 0) 0L else length + base
                    update(item.id) {
                        it.copy(
                            totalBytes = total,
                            indeterminate = total <= 0,
                            bytes = base,
                            progress = if (total > 0) ((base * 100) / total).toInt().coerceIn(0, 99) else 0
                        )
                    }

                    java.io.FileOutputStream(target, appending).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(128 * 1024)
                            var written = base
                            var windowStart = System.currentTimeMillis()
                            var windowBytes = 0L
                            var speed = 0L
                            var lastTick = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                windowBytes += read
                                val now = System.currentTimeMillis()
                                val elapsed = now - windowStart
                                if (elapsed >= 400) {
                                    speed = windowBytes * 1000L / elapsed
                                    windowStart = now
                                    windowBytes = 0L
                                }
                                if (now - lastTick > 300) {
                                    lastTick = now
                                    val bytesNow = written
                                    val speedNow = speed
                                    update(item.id) {
                                        it.copy(
                                            bytes = bytesNow,
                                            speed = speedNow,
                                            progress = if (total > 0) ((bytesNow * 100) / total).toInt().coerceIn(0, 99) else it.progress
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                return
            } catch (t: Throwable) {
                lastError = t
                if (find(item.id) == null) throw t
                attempts++
                if (attempts >= 3) break
                Thread.sleep(600L * attempts)
            }
        }
        throw lastError ?: IllegalStateException("download failed")
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
        // Concatenating segments only works for plain MPEG-TS. Fail loudly instead of
        // silently writing a file that will never play.
        if (Regex("#EXT-X-KEY:.*METHOD=(?!NONE)", RegexOption.IGNORE_CASE).containsMatchIn(playlist)) {
            error("Encrypted HLS streams can't be downloaded")
        }
        if (playlist.contains("#EXT-X-MAP")) {
            error("This HLS stream (fMP4) can't be downloaded")
        }

        val segments = playlist.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(playlistUrl, it) }
        if (segments.isEmpty()) error("playlist has no segments")

        update(item.id) { it.copy(indeterminate = false, totalBytes = 0L) }
        var written = 0L
        var windowStart = System.currentTimeMillis()
        var windowBytes = 0L
        var speed = 0L
        target.outputStream().use { output ->
            segments.forEachIndexed { index, segment ->
                if (find(item.id) == null) throw IllegalStateException("cancelled")
                // some CDNs drop a segment now and then: retry before giving up
                var attempt = 0
                var ok = false
                var lastError: Throwable? = null
                while (attempt < 3 && !ok) {
                    try {
                        newCall(item, request(segment, item)).execute().use { response ->
                            if (!response.isSuccessful) error("segment HTTP ${response.code}")
                            val body = response.body ?: error("empty segment")
                            body.byteStream().use { input ->
                                val buffer = ByteArray(128 * 1024)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    written += read
                                    windowBytes += read
                                }
                            }
                        }
                        ok = true
                    } catch (t: Throwable) {
                        lastError = t
                        if (find(item.id) == null) throw t
                        attempt++
                        Thread.sleep(350L * attempt)
                    }
                }
                if (!ok) error("segment ${index + 1} failed: ${lastError?.message}")
                val now = System.currentTimeMillis()
                val elapsed = now - windowStart
                if (elapsed >= 400) {
                    speed = windowBytes * 1000L / elapsed
                    windowStart = now
                    windowBytes = 0L
                }
                val bytesNow = written
                val speedNow = speed
                update(item.id) {
                    it.copy(
                        bytes = bytesNow,
                        speed = speedNow,
                        progress = (((index + 1) * 100) / segments.size).coerceIn(0, 99)
                    )
                }
            }
        }
    }

    private fun fetchText(item: Item, url: String): String =
        newCall(item, request(url, item)).execute().use { response ->
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

    @Synchronized
    private fun persist() {
        val array = JSONArray()
        _items.value.forEach { item ->
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
