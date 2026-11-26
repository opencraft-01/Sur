package com.primebox.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Small JSON-backed store for settings, repositories and history. */
object Store {

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("primebox", Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------------ theme

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(value) = sp.edit().putInt("theme_mode", value).apply()

    // -------------------------------------------------------------- providers

    var activeProvider: String?
        get() = sp.getString("active_provider", null)
        set(value) = sp.edit().putString("active_provider", value).apply()

    // ----------------------------------------------------------- repositories

    var repositories: List<String>
        get() = runCatching {
            val raw = sp.getString("repos", null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = JSONArray()
            value.distinct().forEach { arr.put(it) }
            sp.edit().putString("repos", arr.toString()).apply()
        }

    fun addRepository(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        repositories = repositories + clean
    }

    fun removeRepository(url: String) {
        repositories = repositories.filterNot { it == url }
    }

    // --------------------------------------------------------------- history

    data class HistoryEntry(
        val title: String,
        val poster: String?,
        val provider: String,
        val url: String,
        val time: Long
    )

    fun history(): List<HistoryEntry> {
        val raw = sp.getString("history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                HistoryEntry(
                    title = o.optString("title"),
                    poster = if (o.has("poster")) o.optString("poster") else null,
                    provider = o.optString("provider"),
                    url = o.optString("url"),
                    time = o.optLong("time")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addHistory(entry: HistoryEntry) {
        val list = history().filterNot { it.url == entry.url && it.provider == entry.provider }
        val updated = (listOf(entry) + list).take(60)
        val arr = JSONArray()
        updated.forEach { item ->
            arr.put(
                JSONObject()
                    .put("title", item.title)
                    .put("poster", item.poster ?: "")
                    .put("provider", item.provider)
                    .put("url", item.url)
                    .put("time", item.time)
            )
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() {
        sp.edit().remove("history").apply()
    }

    // ----------------------------------------------------------- raw values

    fun getRaw(key: String): String? = sp.getString(key, null)

    fun putRaw(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    // ------------------------------------------------------ recent searches

    fun recentSearches(): List<String> {
        val raw = sp.getString("recent_searches", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun addRecentSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + recentSearches().filterNot { it.equals(clean, true) }).take(10)
        val arr = JSONArray()
        updated.forEach { arr.put(it) }
        sp.edit().putString("recent_searches", arr.toString()).apply()
    }

    fun clearRecentSearches() {
        sp.edit().remove("recent_searches").apply()
    }

    /** Timestamp of the last silent update check. */
    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    /** Highest version we already offered to the user. */
    var lastPromptedUpdate: Int
        get() = sp.getInt("last_prompted_update", 0)
        set(value) = sp.edit().putInt("last_prompted_update", value).apply()

    // ------------------------------------------------------------ bookmarks

    var autoplayNext: Boolean
        get() = sp.getBoolean("autoplay_next", true)
        set(value) = sp.edit().putBoolean("autoplay_next", value).apply()
}
