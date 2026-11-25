package com.lagradost.cloudstream3

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast

/**
 * PrimeBox compatibility shim.
 *
 * CS3 extensions are compiled against `com.lagradost.cloudstream3.CloudStreamApp`
 * for key/value storage. This class keeps that symbol (and its companion API)
 * available so existing extensions link correctly.
 */
class CloudStreamApp {

    companion object {
        private var prefs: SharedPreferences? = null

        fun init(context: Context) {
            if (prefs == null) {
                prefs = context.applicationContext
                    .getSharedPreferences("extension_storage", Context.MODE_PRIVATE)
            }
        }

        fun setKey(attr: String, value: String) {
            prefs?.edit()?.putString(attr, value)?.apply()
        }

        fun setKey(attr: String, value: Int) {
            prefs?.edit()?.putInt(attr, value)?.apply()
        }

        fun setKey(attr: String, value: Boolean) {
            prefs?.edit()?.putBoolean(attr, value)?.apply()
        }

        fun setKey(attr: String, value: Long) {
            prefs?.edit()?.putLong(attr, value)?.apply()
        }

        fun setKey(attr: String, value: Float) {
            prefs?.edit()?.putFloat(attr, value)?.apply()
        }

        fun getKey(attr: String): String? = prefs?.getString(attr, null)

        fun getKey(attr: String, defValue: Int): Int = prefs?.getInt(attr, defValue) ?: defValue

        fun getKey(attr: String, defValue: Boolean): Boolean =
            prefs?.getBoolean(attr, defValue) ?: defValue

        fun getKey(attr: String, defValue: Long): Long = prefs?.getLong(attr, defValue) ?: defValue

        fun getKey(attr: String, defValue: Float): Float =
            prefs?.getFloat(attr, defValue) ?: defValue

        fun removeKey(attr: String) {
            prefs?.edit()?.remove(attr)?.apply()
        }

        fun showToast(message: String) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }

        internal var appContext: Context? = null
    }
}
