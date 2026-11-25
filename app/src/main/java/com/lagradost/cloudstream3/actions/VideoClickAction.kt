package com.lagradost.cloudstream3.actions

import android.content.Context
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * PrimeBox shim for the CS3 "video click action" extension point.
 * Extensions may register actions; PrimeBox shows them in the player sheet.
 */
abstract class VideoClickAction {
    abstract val name: String
    var sourcePlugin: String? = null

    open val requiresExplicitAction: Boolean = false

    /** Return true if the action was handled. */
    abstract suspend fun onClick(link: ExtractorLink, context: Context?): Boolean
}

object VideoClickActionHolder {
    val allVideoClickActions = mutableListOf<VideoClickAction>()
}
