package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

/**
 * Base class that CS3 extensions extend. Kept API-compatible with CloudStream 3
 * so existing .cs3 extensions load and run unchanged.
 */
abstract class Plugin : BasePlugin() {

    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }

    /** Populated when the extension declares requiresResources = true */
    var resources: Resources? = null

    /** Lets an extension add a "settings" button for itself. */
    var openSettings: ((context: Context) -> Unit)? = null
}
