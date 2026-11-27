package com.primebox.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** CineGlass accent tokens (kept for older call sites). */
val Accent = Color(0xFFE50914)
val AccentDark = Color(0xFFB00610)
val OnAccent = Color(0xFFFFFFFF)

/**
 * App theme. The whole visual language now comes from the CineGlass design
 * (obsidian gradient, frosted glass surfaces, red glow accent).
 */
@Composable
fun PrimeBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CineTheme(if (darkTheme) 2 else 1) { content() }
}
