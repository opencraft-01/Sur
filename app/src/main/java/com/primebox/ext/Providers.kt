package com.primebox.ext

import com.lagradost.cloudstream3.MainAPI
import com.primebox.data.Store

object Providers {

    fun all(): List<MainAPI> = ExtensionManager.providers().sortedBy { it.name }

    /** Stable identity: provider class + the plugin file it came from. */
    private fun keyOf(api: MainAPI): String =
        "${api.javaClass.name}|${api.sourcePlugin ?: ""}"

    fun select(api: MainAPI) {
        Store.activeProvider = api.name
        Store.activeProviderKey = keyOf(api)
    }

    /**
     * Restores the saved provider. Matching is by the stable key first (several
     * extensions can share a display name), then by name. The fallback is for
     * display only: it must never overwrite what the user picked, otherwise a
     * startup race with plugin loading silently changes the selection.
     */
    fun active(): MainAPI? {
        val list = all()
        if (list.isEmpty()) return null

        val savedKey = Store.activeProviderKey
        if (!savedKey.isNullOrBlank()) {
            list.firstOrNull { keyOf(it) == savedKey }?.let { return it }
        }
        val savedName = Store.activeProvider
        if (!savedName.isNullOrBlank()) {
            list.firstOrNull { it.name == savedName }?.let { return it }
        }
        return list.firstOrNull()
    }
}
