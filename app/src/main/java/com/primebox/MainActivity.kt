package com.primebox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.primebox.compose.AppBackground
import com.primebox.compose.PrimeBoxRoot
import com.primebox.compose.PrimeBoxTheme
import com.primebox.data.Store

/**
 * Single activity host. Every screen is a Jetpack Compose destination, the same
 * stack CloudStream is built on.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)

        setContent {
            var themeMode by remember { mutableStateOf(Store.themeMode) }
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            PrimeBoxTheme(darkTheme = dark) {
                AppBackground {
                    PrimeBoxRoot(
                        themeMode = themeMode,
                        onThemeChange = {
                            Store.themeMode = it
                            themeMode = it
                        }
                    )
                }
            }
        }
    }
}
