package com.primebox.data

import com.lagradost.cloudstream3.SearchResponse

/** UI model for one poster card — decoupled from the library type so it can be cached. */
data class Card(
    val name: String,
    val url: String,
    val poster: String?,
    val type: String?,
    val quality: String?,
    val api: String?
) {
    companion object {
        fun from(item: SearchResponse): Card = Card(
            name = item.name,
            url = item.url,
            poster = item.posterUrl,
            type = item.type?.name,
            quality = item.quality?.name,
            api = item.apiName
        )
    }
}
