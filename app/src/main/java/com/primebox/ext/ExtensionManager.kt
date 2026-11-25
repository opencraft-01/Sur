package com.primebox.ext

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import dalvik.system.PathClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * PrimeBox extension engine.
 *
 * Compatible with CloudStream 3 `.cs3` extension files and CloudStream extension
 * repository JSON indexes.
 */
object ExtensionManager {

    private const val TAG = "PrimeBoxExt"
    const val EXT_DIR = "Extensions"

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

    fun loaded(): List<Loaded> = loadedMap.values.toList()

    fun providers(): List<MainAPI> = APIHolder.allProviders.toList()

    fun extensionDir(context: Context): File =
        File(context.filesDir, EXT_DIR).apply { mkdirs() }

    /** The folder CS3 uses for drop-in local extensions. */
    fun localDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "Cloudstream3/plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ------------------------------------------------------------------ loading

    fun loadAll(context: Context) {
        localDir().listFiles()?.forEach { file ->
            if (file.isFile && file.length() > 0) loadFile(context, file, isLocal = true)
        }
        extensionDir(context).listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".cs3")) loadFile(context, file, isLocal = false)
        }
    }

    fun loadFile(context: Context, file: File, isLocal: Boolean = false): Boolean {
        val path = file.absolutePath
        if (loadedMap.containsKey(path)) return true
        return try {
            val loader = PathClassLoader(path, context.classLoader)
            val manifestText = loader.getResourceAsStream("manifest.json")
                ?.bufferedReader()?.use { it.readText() }
            if (manifestText.isNullOrBlank()) {
                Log.w(TAG, "no manifest.json in ${file.name}")
                return false
            }
            val json = JSONObject(manifestText)
            val className = json.optString("pluginClassName")
            val version = json.optInt("version", Int.MIN_VALUE)
            val name = json.optString("name").ifBlank { file.nameWithoutExtension }
            if (className.isBlank()) {
                Log.w(TAG, "no pluginClassName in ${file.name}")
                return false
            }

            val clazz = loader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = path

            if (json.optBoolean("requiresResources", false)) {
                try {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath = AssetManager::class.java
                        .getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assets, path)
                    @Suppress("DEPRECATION")
                    val res = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
                    (instance as? Plugin)?.resources = res
                } catch (t: Throwable) {
                    Log.w(TAG, "resource attach failed for $name", t)
                }
            }

            if (instance is Plugin) instance.load(context) else instance.load()

            val mine = APIHolder.allProviders.toList().filter { it.sourcePlugin == path }
            loadedMap[path] = Loaded(name, version, path, mine, isLocal)
            Log.i(TAG, "loaded $name (v$version) with ${mine.size} providers")
            true
        } catch (t: Throwable) {
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
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return false to "Could not read file"
            val ok = loadFile(context, target)
            ok to if (ok) "Installed ${target.name}" else "File loaded but not a valid .cs3"
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            false to (t.message ?: "Import failed")
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

    // ------------------------------------------------------------------- repos

    suspend fun fetchRepo(repoUrl: String): List<RepoPlugin> {
        val text = app.get(repoUrl).text
        val root = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        val array = when (root) {
            is JSONArray -> root
            else -> (root as JSONObject).optJSONArray("plugins") ?: JSONArray()
        }
        val out = ArrayList<RepoPlugin>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            val authors = ArrayList<String>()
            o.optJSONArray("authors")?.let { a ->
                for (j in 0 until a.length()) authors.add(a.optString(j))
            }
            out.add(
                RepoPlugin(
                    repoUrl = repoUrl,
                    name = o.optString("name").ifBlank { o.optString("internalName") },
                    internalName = o.optString("internalName").ifBlank { o.optString("name") },
                    url = url,
                    version = o.optInt("version", 0),
                    fileHash = if (o.has("fileHash")) o.optString("fileHash") else null,
                    description = if (o.has("description")) o.optString("description") else null,
                    authors = authors,
                    iconUrl = if (o.has("iconUrl")) o.optString("iconUrl") else null,
                    language = if (o.has("language")) o.optString("language") else null
                )
            )
        }
        return out
    }

    suspend fun install(context: Context, plugin: RepoPlugin): Pair<Boolean, String> {
        return try {
            val target = File(extensionDir(context), sanitize(plugin.internalName + ".cs3"))
            val tmp = File(context.cacheDir, "dl_${System.currentTimeMillis()}.cs3")
            val body = app.get(plugin.url).okhttpResponse.body
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!plugin.fileHash.isNullOrBlank()) {
                val hash = sha256(tmp)
                if (hash != plugin.fileHash) {
                    tmp.delete()
                    return false to "Hash mismatch — download rejected"
                }
            }
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            val ok = loadFile(context, target)
            ok to if (ok) "Installed ${plugin.name}" else "Downloaded, but extension failed to load"
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            false to (t.message ?: "Install failed")
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
