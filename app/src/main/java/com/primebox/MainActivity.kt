package com.primebox

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.primebox.compose.PrimeBoxRoot
import com.primebox.compose.PrimeBoxTheme
import com.primebox.data.Store

/** Single activity host. Every screen is a Jetpack Compose destination styled with Material 3. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()

        setContent {
            var themeMode by remember { mutableIntStateOf(Store.themeMode) }
            var dynamicColor by remember { mutableStateOf(Store.dynamicColor) }
            val dark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            // keep status / navigation bar icons readable when the in-app theme differs from the system one
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
                )
                onDispose { }
            }

            PrimeBoxTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PrimeBoxRoot(
                        themeMode = themeMode,
                        onThemeChange = {
                            Store.themeMode = it
                            themeMode = it
                        },
                        dynamicColor = dynamicColor,
                        onDynamicColorChange = {
                            Store.dynamicColor = it
                            dynamicColor = it
                        }
                    )
                }
            }
        }
    }
}
