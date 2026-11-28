package com.primebox

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lagradost.cloudstream3.CloudStreamApp
import com.primebox.data.Downloader
import com.primebox.data.Store
import com.primebox.ext.ExtensionManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application class. It is also Coil's image-loader factory, so every poster in the
 * Compose UI goes through one loader with a browser User-Agent (many CDNs reject
 * the default one), a bounded memory cache and a 64 MB disk cache.
 */
class PrimeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        CloudStreamApp.init(this)
        CloudStreamApp.appContext = this

        // The CS3 library needs a host Context for its WebView based resolvers
        // (Cloudflare / DDoS-Guard). Without it many providers return no links.
        runCatching { com.lagradost.api.setContext(this) }

        AppCompatDelegate.setDefaultNightMode(
            when (Store.themeMode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        Downloader.init(this)

        Thread {
            runCatching { ExtensionManager.loadAll(this) }
        }.start()
    }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", IMAGE_UA).build())
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            // posters rarely send useful cache headers; cache them regardless
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("poster_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    private companion object {
        const val IMAGE_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
