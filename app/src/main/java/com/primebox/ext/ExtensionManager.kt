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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever the set of providers may have changed (load / install / remove). */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** False until the first [loadAll] pass has finished (it runs on a background thread at start-up). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _restartRequired = MutableStateFlow(false)

    /** True after an extension was removed / updated: its old code lives on until the process restarts. */
    val restartRequired: StateFlow<Boolean> = _restartRequired.asStateFlow()

    private fun bump() = _revision.update { it + 1 }

    /** Last human readable failure, shown in the UI. */
    var lastError: String? = null
        private set

    // loadAll() runs on a worker thread while the UI reads these maps
    fun loaded(): List<Loaded> = synchronized(loadedMap) { loadedMap.values.toList() }

    fun providers(): List<MainAPI> {
        // the list is mutated by extensions while they load; retry instead of crashing
        repeat(3) {
            try {
                return APIHolder.allProviders.toList()
            } catch (e: ConcurrentModificationException) {
                Thread.sleep(20)
            }
        }
        return emptyList()
    }

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
        try {
            runCatching {
                localDir().listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) loadFile(context, file, isLocal = true)
                }
            }
            extensionDir(context).listFiles()?.forEach { file ->
                if (file.isFile) loadFile(context, file, isLocal = false)
            }
        } finally {
            _ready.value = true
            bump()
        }
    }

    // ------------------------------------------------------------- diagnostics

    private val logLines = mutableListOf<String>()

    fun log(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(logLines) {
            logLines.add("$stamp  $line")
            while (logLines.size > 300) logLines.removeAt(0)
        }
        Log.i(TAG, line)
    }

    fun diagnostics(): List<String> = synchronized(logLines) { logLines.toList() }

    fun diagnosticsText(context: Context): String = buildString {
        appendLine("PrimeBox extension diagnostics")
        appendLine("app files: ${context.filesDir.absolutePath}")
        appendLine("external : ${context.getExternalFilesDir(null)?.absolutePath}")
        appendLine("providers: ${APIHolder.allProviders.size}")
        appendLine("loaded   : ${loaded().size}")
        appendLine("---")
        diagnostics().forEach { appendLine(it) }
    }

    // ----------------------------------------------------------- dex staging

    /**
     * CloudStream loads extensions from internal storage with the file marked
     * read-only. Android 14+ also accepts app-specific external storage, and some
     * devices only work on one of the two, so both are staged and tried in turn.
     */
    private fun stageCopies(context: Context, file: File): List<File> {
        val copies = ArrayList<File>(2)

        fun stage(dir: File): File? = try {
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, file.name)
            // size alone is not enough: an updated extension can have the same byte length
            if (!dest.exists() || dest.length() != file.length() || dest.lastModified() != file.lastModified()) {
                file.copyTo(dest, overwrite = true)
                dest.setLastModified(file.lastModified())
            }
            runCatching { dest.setReadOnly() }
            dest
        } catch (t: Throwable) {
            log("stage failed in ${dir.name}: ${t.javaClass.simpleName}: ${t.message}")
            null
        }

        stage(File(context.filesDir, "dex"))?.let { copies.add(it) }
        context.getExternalFilesDir(null)?.let { ext -> stage(File(ext, "plugins"))?.let { copies.add(it) } }
        return copies
    }

    fun loadFile(context: Context, file: File, isLocal: Boolean = false): Boolean {
        val path = file.absolutePath
        if (synchronized(loadedMap) { loadedMap.containsKey(path) }) return true

        log("load ${file.name} (${file.length()} bytes, local=$isLocal)")
        if (file.length() == 0L) {
            lastError = "${file.name}: file is empty"
            log("  ✗ ${lastError}")
            return false
        }

        val copies = stageCopies(context, file)
        if (copies.isEmpty()) {
            lastError = "${file.name}: could not stage the file for loading"
            log("  ✗ ${lastError}")
            return false
        }

        var lastFailure: String? = null
        for (copy in copies) {
            try {
                loadFrom(context, file, copy, isLocal)
                lastError = null
                bump()
                return true
            } catch (t: Throwable) {
                lastFailure = "${t.javaClass.simpleName}: ${t.message}"
                log("  ✗ via ${copy.parentFile?.name}: $lastFailure")
            }
        }
        lastError = "${file.name}: $lastFailure"
        return false
    }

    private fun loadFrom(context: Context, original: File, dexFile: File, isLocal: Boolean) {
        val loader = PathClassLoader(dexFile.absolutePath, context.classLoader)
        val manifestText = loader.getResourceAsStream("manifest.json")
            ?.bufferedReader()?.use { it.readText() }
        if (manifestText.isNullOrBlank()) {
            throw IllegalStateException("no manifest.json inside the extension (is this a .cs3 file?)")
        }
        val json = JSONObject(manifestText)
        val className = json.optString("pluginClassName")
        val version = json.optInt("version", Int.MIN_VALUE)
        val name = json.optString("name").ifBlank { original.nameWithoutExtension }
        if (className.isBlank()) {
            throw IllegalStateException("manifest.json has no pluginClassName")
        }
        log("  manifest: $name v$version → $className")

        val clazz = loader.loadClass(className)
        val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
        instance.filename = dexFile.absolutePath

        if (json.optBoolean("requiresResources", false)) {
            try {
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assets, dexFile.absolutePath)
                @Suppress("DEPRECATION")
                val res = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                (instance as? Plugin)?.resources = res
                log("  resources attached")
            } catch (t: Throwable) {
                log("  resources failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        if (instance is Plugin) instance.load(context) else instance.load()

        val mine = APIHolder.allProviders.toList()
            .filter { it.sourcePlugin == dexFile.absolutePath || it.sourcePlugin == original.absolutePath }
        synchronized(loadedMap) {
            loadedMap[original.absolutePath] = Loaded(name, version, original.absolutePath, mine, isLocal)
        }
        log("  ✓ loaded $name (v$version) with ${mine.size} provider(s)")
        if (mine.isEmpty()) {
            log("  ! no providers registered by this extension")
        }
    }

    /**
     * Removes an extension file. Providers registered by it stay alive until the
     * app restarts (the extension API has no unregister hook).
     */
    fun unload(context: Context, item: Loaded): Boolean {
        return try {
            synchronized(loadedMap) { loadedMap.remove(item.path) }
            val file = File(item.path)
            // also drop the staged dex copies, otherwise a stale copy could be picked up again
            runCatching { File(File(context.filesDir, "dex"), file.name).delete() }
            runCatching {
                context.getExternalFilesDir(null)?.let { File(File(it, "plugins"), file.name).delete() }
            }
            val removed = if (file.exists()) file.delete() else true
            _restartRequired.value = true
            bump()
            removed
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

    // ------------------------------------------------------------ url handling

    data class Preset(val name: String, val url: String)

    /** Known-good repositories offered as one-tap presets. */
    val PRESETS = listOf(
        Preset(
            "Cloudstream providers repository (official)",
            "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"
        )
    )

    private val noRedirectClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * Turns whatever the user pasted into a fetchable URL — same rules CloudStream uses:
     * `cloudstreamrepo://…`, `https://cs.repo/?…`, a bare `github.com/user/repo` link,
     * a GitHub blob link, or a short code (resolved through py.md / cutt.ly).
     */
    suspend fun normalizeRepoUrl(input: String): String = withContext(Dispatchers.IO) {
        var url = input.trim()
        if (url.isEmpty()) return@withContext url

        url = url.removePrefix("cloudstreamrepo://")
        url = Regex("^https?://cs\\.repo/\\??").replace(url, "")

        // short codes like "abc123" or "!abc123"
        if (Regex("^[a-zA-Z0-9!_-]+$").matches(url)) {
            val resolved = resolveShortCode(url)
            if (resolved != null) return@withContext resolved
        }

        // GitHub web links -> raw content
        val gh = Regex("^https?://(www\\.)?github\\.com/([^/]+)/([^/]+)(/.*)?$").find(url)
        if (gh != null) {
            val user = gh.groupValues[2]
            val repo = gh.groupValues[3].removeSuffix(".git")
            val rest = gh.groupValues[4]
            url = when {
                rest.startsWith("/blob/") ->
                    "https://raw.githubusercontent.com/$user/$repo/" + rest.removePrefix("/blob/")
                rest.startsWith("/raw/") ->
                    "https://raw.githubusercontent.com/$user/$repo/" + rest.removePrefix("/raw/")
                else -> "https://raw.githubusercontent.com/$user/$repo/master/repo.json"
            }
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        url
    }

    private fun resolveShortCode(code: String): String? {
        val attempts = if (code.startsWith("!")) {
            listOf("https://py.md/${code.removePrefix("!")}")
        } else {
            listOf("https://py.md/$code", "https://cutt.ly/$code")
        }
        for (attempt in attempts) {
            runCatching {
                val request = Request.Builder().url(attempt).header("User-Agent", USER_AGENT).build()
                noRedirectClient.newCall(request).execute().use { response ->
                    val location = response.header("Location")
                    if (location != null &&
                        !location.contains("404") &&
                        !location.removeSuffix("/").endsWith("py.md") &&
                        !location.removeSuffix("/").endsWith("cutt.ly")
                    ) {
                        return location
                    }
                }
            }
        }
        return null
    }

    /** raw.githubusercontent.com -> cdn.jsdelivr.net mirror (helps on blocked networks). */
    private fun jsdelivrMirror(url: String): String? {
        val match = Regex("^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(.+)$").find(url) ?: return null
        val user = match.groupValues[1]
        val repo = match.groupValues[2]
        val rest = match.groupValues[3]
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    private fun httpGetOnce(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ${response.message} — ${body.take(100).replace("\n", " ")}")
            }
            body
        }
    }

    /** Tries the URL, then the jsDelivr mirror, then master/main variants of repo.json. */
    private fun httpGet(url: String): String {
        val candidates = LinkedHashSet<String>()
        candidates.add(url)
        jsdelivrMirror(url)?.let { candidates.add(it) }
        if (url.contains("/master/repo.json")) {
            candidates.add(url.replace("/master/repo.json", "/main/repo.json"))
        } else if (url.contains("/main/repo.json")) {
            candidates.add(url.replace("/main/repo.json", "/master/repo.json"))
        }

        var last: Throwable? = null
        for (candidate in candidates) {
            try {
                return httpGetOnce(candidate)
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("Could not fetch $url")
    }

    private fun parseJson(text: String): Any {
        val trimmed = text.trimStart()
        if (trimmed.startsWith("<")) {
            throw IllegalStateException("Server returned a web page, not JSON — use the raw repo.json link")
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

    data class RepoInfo(
        val url: String,
        val name: String,
        val description: String?,
        val plugins: List<RepoPlugin>
    )

    /**
     * Resolves a repository URL into plugins, accepting every shape found in the wild:
     *  1. `{ "pluginLists": [url, …] }`   ← real CloudStream repositories
     *  2. `{ "plugins": [ … ] }`
     *  3. a bare JSON array of plugins
     *  4. a bare JSON array of plugin-list URLs
     */
    suspend fun fetchRepo(rawUrl: String): RepoInfo = withContext(Dispatchers.IO) {
        val repoUrl = normalizeRepoUrl(rawUrl)
        val root = parseJson(httpGet(repoUrl))

        var repoName = "Repository"
        var repoDescription: String? = null
        val arrays = ArrayList<JSONArray>()

        fun collect(node: Any?) {
            when (node) {
                is JSONArray -> {
                    val firstObject = (0 until node.length())
                        .mapNotNull { node.optJSONObject(it) }
                        .firstOrNull()
                    if (firstObject != null && (firstObject.has("url") || firstObject.has("jarUrl"))) {
                        arrays.add(node)
                    } else {
                        for (i in 0 until node.length()) {
                            val value = node.optString(i)
                            if (value.startsWith("http") || value.endsWith(".json")) {
                                runCatching { collect(parseJson(httpGet(resolve(repoUrl, value)))) }
                            }
                        }
                    }
                }

                is JSONObject -> {
                    node.optString("name").takeIf { it.isNotBlank() }?.let { repoName = it }
                    node.optString("description").takeIf { it.isNotBlank() }?.let { repoDescription = it }
                    node.optJSONArray("pluginLists")?.let { lists ->
                        for (i in 0 until lists.length()) {
                            val listUrl = lists.optString(i)
                            if (listUrl.isNotBlank()) {
                                runCatching { collect(parseJson(httpGet(resolve(repoUrl, listUrl)))) }
                            }
                        }
                    }
                    node.optJSONArray("plugins")?.let { collect(it) }
                    node.optJSONArray("data")?.let { collect(it) }
                    node.optJSONArray("extensions")?.let { collect(it) }
                }
            }
        }

        collect(root)
        if (arrays.isEmpty()) {
            throw IllegalStateException("No plugin list found — is this a CloudStream repository link?")
        }

        val out = LinkedHashMap<String, RepoPlugin>()
        arrays.forEach { array ->
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val url = o.optString("url")
                    .ifBlank { o.optString("jarUrl") }
                    .ifBlank { o.optString("apkUrl") }
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
        RepoInfo(repoUrl, repoName, repoDescription, out.values.toList())
    }

    suspend fun install(context: Context, plugin: RepoPlugin): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val target = File(extensionDir(context), sanitize(plugin.internalName + ".cs3"))
            val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}.cs3")

            val candidates = LinkedHashSet<String>()
            candidates.add(plugin.url)
            jsdelivrMirror(plugin.url)?.let { candidates.add(it) }

            var lastError: String? = null
            var downloaded = false
            for (candidate in candidates) {
                try {
                    val request = Request.Builder().url(candidate).header("User-Agent", USER_AGENT).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastError = "HTTP ${response.code} from $candidate"
                            return@use
                        }
                        val body = response.body ?: return@use
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                        downloaded = tmp.length() > 0
                    }
                    if (downloaded) break
                } catch (t: Throwable) {
                    lastError = "${t.javaClass.simpleName}: ${t.message}"
                }
            }
            if (!downloaded) {
                return@withContext false to (lastError ?: "Download failed")
            }

            if (!plugin.fileHash.isNullOrBlank()) {
                // accept both "sha256-<hex>" and a bare hex digest
                val expected = plugin.fileHash.trim().removePrefix("sha256-").lowercase()
                val actual = sha256(tmp).removePrefix("sha256-").lowercase()
                if (actual != expected) {
                    tmp.delete()
                    return@withContext false to "Hash mismatch — download rejected"
                }
            }
            val alreadyLoaded = synchronized(loadedMap) { loadedMap.containsKey(target.absolutePath) }
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            if (alreadyLoaded) {
                // the old classes are still in memory and cannot be swapped out at runtime
                _restartRequired.value = true
                return@withContext true to "Updated ${plugin.name} — restart PrimeBox to apply"
            }
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
