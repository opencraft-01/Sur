package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.widget.Toast

/** PrimeBox shim: extensions call CommonActivity.showToast(...). */
object CommonActivity {
    @JvmStatic
    fun showToast(activity: Activity?, message: String) {
        if (activity == null) return
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun showToast(context: Context?, message: String) {
        if (context == null) return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun showToast(message: String) {
        CloudStreamApp.showToast(message)
    }
}
