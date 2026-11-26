package com.primebox.sample

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

/**
 * Reference extension used to verify that PrimeBox can load CS3 extensions.
 * It needs no network: every screen is generated locally.
 */
class PrimeBoxTestPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(PrimeBoxTestProvider())
    }
}

class PrimeBoxTestProvider : MainAPI() {
    override var mainUrl = "https://primebox.test"
    override var name = "PrimeBox Test"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = listOf(MainPageData("Test Row", "", false))

    private fun poster(seed: Int) = "https://picsum.photos/seed/$seed/300/450"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = (1..10).map { index ->
            newMovieSearchResponse("Test Movie $index", "$mainUrl/movie/$index", TvType.Movie) {
                posterUrl = poster(index)
            }
        }
        return newHomePageResponse(listOf(HomePageList("Test Row", items)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return (1..5).map { index ->
            newMovieSearchResponse("$query $index", "$mainUrl/movie/$index", TvType.Movie) {
                posterUrl = poster(index + 40)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast('/').toIntOrNull() ?: 1
        return newMovieLoadResponse("Test Movie $id", url, TvType.Movie, url) {
            posterUrl = poster(id)
            plot = "PrimeBox test entry. If you can read this, the extension system works end to end."
            year = 2026
            duration = 42
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            newExtractorLink(
                source = name,
                name = "Test Stream (not playable)",
                url = "https://primebox.test/sample.m3u8"
            ) {
                this.referer = ""
                this.quality = 720
            }
        )
        return true
    }
}
