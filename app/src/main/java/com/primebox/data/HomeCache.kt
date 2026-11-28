package com.primebox.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk cache for the home screen so the last loaded content is visible instantly
 * (even offline) and can be refreshed row by row in the background.
 */
object HomeCache {

    data class Row(val name: String, val items: List<Card>, val horizontal: Boolean = false)
    data class Home(val provider: String, val time: Long, val rows: List<Row>)

    private const val FILE = "home_cache.json"

    private fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun save(context: Context, provider: String, rows: List<Row>) {
        runCatching {
            val root = read(context)
            val array = JSONArray()
            rows.forEach { row ->
                val items = JSONArray()
                row.items.forEach { card ->
                    items.put(
                        JSONObject()
                            .put("name", card.name)
                            .put("url", card.url)
                            .put("poster", card.poster ?: "")
                            .put("type", card.type ?: "")
                            .put("quality", card.quality ?: "")
                            .put("api", card.api ?: "")
                    )
                }
                array.put(
                    JSONObject()
                        .put("name", row.name)
                        .put("horizontal", row.horizontal)
                        .put("items", items)
                )
            }
            root.put(
                provider,
                JSONObject()
                    .put("time", System.currentTimeMillis())
                    .put("rows", array)
            )
            // keep at most 4 providers
            if (root.length() > 4) {
                val keep = root.keys().asSequence().toList().takeLast(4)
                val trimmed = JSONObject()
                keep.forEach { key -> trimmed.put(key, root.optJSONObject(key)) }
                file(context).writeText(trimmed.toString())
            } else {
                file(context).writeText(root.toString())
            }
        }
    }

    @Synchronized
    fun load(context: Context, provider: String): Home? {
        return runCatching {
            val entry = read(context).optJSONObject(provider) ?: return null
            val rowsArray = entry.optJSONArray("rows") ?: return null
            val rows = ArrayList<Row>()
            for (i in 0 until rowsArray.length()) {
                val rowObj = rowsArray.optJSONObject(i) ?: continue
                val itemsArray = rowObj.optJSONArray("items") ?: continue
                val items = ArrayList<Card>()
                for (j in 0 until itemsArray.length()) {
                    val o = itemsArray.optJSONObject(j) ?: continue
                    items.add(
                        Card(
                            name = o.optString("name"),
                            url = o.optString("url"),
                            poster = o.optString("poster").ifBlank { null },
                            type = o.optString("type").ifBlank { null },
                            quality = o.optString("quality").ifBlank { null },
                            api = o.optString("api").ifBlank { null }
                        )
                    )
                }
                rows.add(Row(rowObj.optString("name"), items, rowObj.optBoolean("horizontal", false)))
            }
            Home(provider, entry.optLong("time"), rows)
        }.getOrNull()
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun read(context: Context): JSONObject {
        val f = file(context)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }
}
