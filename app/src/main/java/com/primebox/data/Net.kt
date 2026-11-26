package com.primebox.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Network helpers used to keep mobile data usage low. */
object Net {

    fun isMetered(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            cm.isActiveNetworkMetered
        } catch (t: Throwable) {
            false
        }
    }

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (t: Throwable) {
            true
        }
    }

    /** True when the app should avoid optional traffic (thumbnails, prefetch, auto refresh). */
    fun shouldSaveData(context: Context): Boolean = Store.dataSaver || isMetered(context)
}
