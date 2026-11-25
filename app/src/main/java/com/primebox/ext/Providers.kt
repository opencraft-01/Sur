package com.primebox.ext

import com.lagradost.cloudstream3.MainAPI
import com.primebox.data.Store

object Providers {

    fun all(): List<MainAPI> = ExtensionManager.providers().sortedBy { it.name }

    fun active(): MainAPI? {
        val list = all()
        if (list.isEmpty()) return null
        val name = Store.activeProvider
        return list.firstOrNull { it.name == name } ?: list.first().also {
            Store.activeProvider = it.name
        }
    }

    fun select(api: MainAPI) {
        Store.activeProvider = api.name
    }
}
