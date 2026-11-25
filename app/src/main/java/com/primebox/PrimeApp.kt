package com.primebox

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.lagradost.cloudstream3.CloudStreamApp
import com.primebox.data.Store
import com.primebox.data.Downloader
import com.primebox.ext.ExtensionManager

class PrimeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        CloudStreamApp.init(this)
        CloudStreamApp.appContext = this

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
}
