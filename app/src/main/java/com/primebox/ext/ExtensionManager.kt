package com.primebox.ext

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * PrimeBox extension engine.
 *
 * Compatible with CloudStream 3 `.cs3` extension files and CloudStream extension
 * repositories (both the `plugins` array form and the `pluginLists` form that real
 * CloudStream repositories use).
 */
object ExtensionManager {

    private const val TAG = "PrimeBoxExt"
    const val EXT_DIR = "Extensions"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    data class Loaded(
        val name: String,
        val version: Int,
        val path: String,
        val providers: List<MainAPI>,
        val isLocal: Boolean
    )

    data class RepoPlugin(
        val repoUrl: String,
        val name: String,
        val internalName: String,
        val url: String,
        val version: Int,
        val fileHash: String?,
        val description: String?,
        val authors: List<String>,
        val iconUrl: String?,
        val language: String?
    )

    private val loadedMap = LinkedHashMap<String, Loaded>()

    /** Last human readable failure, shown in the UI. */
    var lastError: String? = null
        private set

    fun loaded(): List<Loaded> = loadedMap.values.toList()

    fun providers(): List<MainAPI> = APIHolder.allProviders.toList()

    fun extensionDir(context: Context): File =
        File(context.filesDir, EXT_DIR).apply { mkdirs() }

    /** Folder CS3 uses for drop-in local extensions. */
    fun localDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "Cloudstream3/plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ------------------------------------------------------------------ loading

    fun loadAll(context: Context) {
        runCatching {
            localDir().listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) loadFile(context, file, isLocal = true)
            }
        }
        extensionDir(context).listFiles()?.forEach { file ->
            if (file.isFile) loadFile(context, file, isLocal = false)
        }
    }

    /**
     * Android 14+ refuses to load dex from a writable location, so every plugin is
     * copied into app-specific external storage and marked read-only first.
     */
    private fun dexCopy(context: Context, file: File): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "plugins").apply { mkdirs() }
        val dest = File(dir, file.name)
        if (!dest.exists() || dest.length() != file.length()) {
            file.copyTo(dest, overwrite = true)
            dest.setLastModified(file.lastModified())
            runCatching { dest.setReadOnly() }
        }
        return dest
    }

    fun loadFile(context: Context, file: File, isLocal: Boolean = false): Boolean {
        val path = file.absolutePath
        if (loadedMap.containsKey(path)) return true
        return try {
            val dexFile = dexCopy(context, file)
            val loader = PathClassLoader(dexFile.absolutePath, context.classLoader)
            val manifestText = loader.getResourceAsStream("manifest.json")
                ?.bufferedReader()?.use { it.readText() }
            if (manifestText.isNullOrBlank()) {
                lastError = "${file.name}: no manifest.json inside the extension"
                Log.w(TAG, lastError!!)
                return false
            }
            val json = JSONObject(manifestText)
            val className = json.optString("pluginClassName")
            val version = json.optInt("version", Int.MIN_VALUE)
            val name = json.optString("name").ifBlank { file.nameWithoutExtension }
            if (className.isBlank()) {
                lastError = "${file.name}: manifest.json has no pluginClassName"
                Log.w(TAG, lastError!!)
                return false
            }

            val clazz = loader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = dexFile.absolutePath

            if (json.optBoolean("requiresResources", false)) {
                try {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath = AssetManager::class.java
                        .getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assets, dexFile.absolutePath)
                    @Suppress("DEPRECATION")
                    val res = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                    (instance as? Plugin)?.resources = res
                } catch (t: Throwable) {
                    Log.w(TAG, "resource attach failed for $name", t)
                }
            }

            if (instance is Plugin) instance.load(context) else instance.load()

            val mine = APIHolder.allProviders.toList()
                .filter { it.sourcePlugin == dexFile.absolutePath || it.sourcePlugin == path }
            loadedMap[path] = Loaded(name, version, path, mine, isLocal)
            lastError = null
            Log.i(TAG, "loaded $name (v$version) with ${mine.size} providers")
            true
        } catch (t: Throwable) {
            lastError = "${file.name}: ${t.javaClass.simpleName}: ${t.message}"
            Log.e(TAG, "failed loading ${file.name}", t)
            false
        }
    }

    /**
     * Removes an extension file. Providers registered by it stay alive until the
     * app restarts (the extension API has no unregister hook).
     */
    fun unload(context: Context, item: Loaded): Boolean {
        return try {
            loadedMap.remove(item.path)
            val file = File(item.path)
            if (file.exists()) file.delete() else true
        } catch (t: Throwable) {
            Log.e(TAG, "unload failed", t)
            false
        }
    }

    // ------------------------------------------------------------- local import

    /** Copies a user-picked .cs3 file into the app extension folder and loads it. */
    fun importFromUri(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            val name = queryName(context, uri) ?: "extension.cs3"
            val target = File(extensionDir(context), sanitize(name))
            val input = context.contentResolver.openInputStream(uri)
                ?: return false to "Could not open the picked file"
            input.use { source ->
                target.outputStream().use { output -> source.copyTo(output) }
            }
            if (target.length() == 0L) {
                target.delete()
                return false to "Picked file was empty"
            }
            val ok = loadFile(context, target)
            if (ok) true to "Installed ${target.name}" else false to (lastError ?: "Not a valid .cs3 file")
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            false to "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun queryName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idx = cursor.getColumnIndex("_display_name")
                if (idx >= 0) cursor.getString(idx) else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/')
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.endsWith(".cs3")) cleaned else "$cleaned.cs3"
    }

    /**
     * Fallback importer: scans the public Download folder for `.cs3` files.
     * Works even when the system file picker hides unknown extensions.
     */
    fun scanDownloads(context: Context): Pair<Int, String> {
        val dirs = listOf(
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Downloads"),
            localDir()
        )
        var count = 0
        val errors = ArrayList<String>()
        dirs.filter { it.exists() && it.isDirectory }.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.lowercase().endsWith(".cs3")) {
                    val target = File(extensionDir(context), sanitize(file.name))
                    runCatching { file.copyTo(target, overwrite = true) }
                    if (loadFile(context, target)) count++ else errors.add(lastError ?: file.name)
                }
            }
        }
        return when {
            count > 0 -> count to "Installed $count extension(s)"
            errors.isNotEmpty() -> 0 to errors.first()
            else -> 0 to "No .cs3 file found in Download or /Cloudstream3/plugins"
        }
    }

    // ------------------------------------------------------------------- repos

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ${response.message} — ${body.take(120).replace("\n", " ")}")
            }
            body
        }
    }

    private fun parseJson(text: String): Any {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<")) {
            throw IllegalStateException("Server returned HTML, not JSON (wrong URL?)")
        }
        return if (trimmed.startsWith("[")) JSONArray(text) else JSONObject(text)
    }

    private fun resolve(base: String, relative: String): String {
        val rel = relative.trim()
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        return try {
            java.net.URI(base).resolve(rel).toString()
        } catch (t: Throwable) {
            rel
        }
    }

    /**
     * Resolves a repository URL into plugins.
     *
     * Supports the three shapes found in the wild:
     *  1. a bare JSON array of plugins
     *  2. `{ "plugins": [...] }`
     *  3. `{ "pluginLists": ["https://…/plugins.json", …] }`  ← real CloudStream repos
     */
    suspend fun fetchRepo(repoUrl: String): List<RepoPlugin> = withContext(Dispatchers.IO) {
        val root = parseJson(httpGet(repoUrl))
        val arrays = ArrayList<JSONArray>()

        fun collect(node: Any?) {
            when (node) {
                is JSONArray -> {
                    // A plugin list is an array of objects that have a `url` field.
                    val firstObject = (0 until node.length())
                        .mapNotNull { node.optJSONObject(it) }
                        .firstOrNull()
                    if (firstObject != null && firstObject.has("url")) {
                        arrays.add(node)
                    } else {
                        // Otherwise treat it as a list of URLs to plugin files.
                        for (i in 0 until node.length()) {
                            val value = node.optString(i)
                            if (value.startsWith("http") || value.endsWith(".json")) {
                                runCatching {
                                    collect(parseJson(httpGet(resolve(repoUrl, value))))
                                }
                            }
                        }
                    }
                }

                is JSONObject -> {
                    node.optJSONArray("plugins")?.let { collect(it) }
                    node.optJSONArray("pluginLists")?.let { lists ->
                        for (i in 0 until lists.length()) {
                            val listUrl = lists.optString(i)
                            if (listUrl.isNotBlank()) {
                                runCatching {
                                    collect(parseJson(httpGet(resolve(repoUrl, listUrl))))
                                }
                            }
                        }
                    }
                    // some repos wrap the list in "data" or "extensions"
                    node.optJSONArray("data")?.let { collect(it) }
                    node.optJSONArray("extensions")?.let { collect(it) }
                }
            }
        }

        collect(root)
        if (arrays.isEmpty()) {
            throw IllegalStateException("No plugin list found — check that this is a CloudStream repository URL")
        }

        val out = LinkedHashMap<String, RepoPlugin>()
        arrays.forEach { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isBlank()) continue
                val authors = ArrayList<String>()
                o.optJSONArray("authors")?.let { a ->
                    for (j in 0 until a.length()) authors.add(a.optString(j))
                }
                val internal = o.optString("internalName").ifBlank { o.optString("name") }
                val plugin = RepoPlugin(
                    repoUrl = repoUrl,
                    name = o.optString("name").ifBlank { internal },
                    internalName = internal,
                    url = resolve(repoUrl, url),
                    version = o.optInt("version", 0),
                    fileHash = if (o.has("fileHash")) o.optString("fileHash") else null,
                    description = if (o.has("description")) o.optString("description") else null,
                    authors = authors,
                    iconUrl = if (o.has("iconUrl")) o.optString("iconUrl") else null,
                    language = if (o.has("language")) o.optString("language") else null
                )
                out[internal.ifBlank { url }] = plugin
            }
        }
        if (out.isEmpty()) {
            throw IllegalStateException("Repository listed no installable extensions")
        }
        out.values.toList()
    }

    suspend fun install(context: Context, plugin: RepoPlugin): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val target = File(extensionDir(context), sanitize(plugin.internalName + ".cs3"))
            val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}.cs3")
            val request = Request.Builder().url(plugin.url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext false to "Download failed: HTTP ${response.code}"
                }
                val body = response.body ?: return@withContext false to "Download failed: empty body"
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (!plugin.fileHash.isNullOrBlank()) {
                val hash = sha256(tmp)
                if (hash != plugin.fileHash) {
                    tmp.delete()
                    return@withContext false to "Hash mismatch — download rejected"
                }
            }
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            val ok = loadFile(context, target)
            ok to if (ok) "Installed ${plugin.name}" else (lastError ?: "Downloaded, but extension failed to load")
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            false to "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return "sha256-$hex"
    }
}
