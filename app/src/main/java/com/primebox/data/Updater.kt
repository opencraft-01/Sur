package com.primebox.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.primebox.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updater: reads the newest GitHub release of the project repository,
 * downloads the APK and opens the system installer.
 */
object Updater {

    private const val TAG = "PrimeBoxUpdate"
    private const val API = "https://api.github.com/repos/opencraft-01/Sur/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Info(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val notes: String
    )

    fun currentCode(): Int = BuildConfig.VERSION_CODE
    fun currentName(): String = BuildConfig.VERSION_NAME

    suspend fun check(): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PrimeBox/${BuildConfig.VERSION_NAME}")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val notes = json.optString("body")
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var url: String? = null
            var codeFromName: Int? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", true)) continue
                url = asset.optString("browser_download_url")
                Regex("-c(\\d+)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { codeFromName = it }
                break
            }
            val apkUrl = url ?: return@runCatching null
            val code = codeFromName
                ?: Regex("versionCode\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@runCatching null
            val name = tag.removePrefix("v").substringBefore("-c")
            Info(name.ifBlank { code.toString() }, code, apkUrl, notes)
        }.onFailure { Log.w(TAG, "update check failed: ${it.message}") }.getOrNull()
    }

    suspend fun download(context: Context, info: Info, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, "PrimeBox-${info.versionCode}.apk")
                val request = Request.Builder().url(info.apkUrl).header("User-Agent", "PrimeBox").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("empty body")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var written = 0L
                            var last = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val pct = ((written * 100) / total).toInt()
                                    if (pct != last) {
                                        last = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                }
                target
            }.onFailure { Log.e(TAG, "download failed", it) }.getOrNull()
        }

    /** Opens the system package installer for the downloaded APK. */
    fun install(context: Context, file: File): Boolean {
        return runCatching {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.onFailure { Log.e(TAG, "install failed", it) }.getOrDefault(false)
    }
}
