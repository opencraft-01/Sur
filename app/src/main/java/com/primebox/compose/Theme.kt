package com.primebox.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Brand colour schemes.
//
// Generated as full Material 3 tonal palettes from the PrimeBox blue seed (#1F80E0) with deep-navy neutrals:
// primary / secondary / tertiary / error / neutral / neutral-variant, mapped to the
// standard light (tone 40/90) and dark (tone 80/30) roles, including the surface
// container ladder. Used when dynamic colour is off or unavailable (Android < 12).
// ---------------------------------------------------------------------------

private val LightScheme = lightColorScheme(
    primary = Color(0xFF025FAC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF535E78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E2FF),
    onSecondaryContainer = Color(0xFF0E1C31),
    tertiary = Color(0xFF7E506F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F0),
    onTertiaryContainer = Color(0xFF340B29),
    error = Color(0xFFB22A22),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDBD3),
    onErrorContainer = Color(0xFF390C00),
    background = Color(0xFFF6FAFF),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFF6FAFF),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFDAE3F1),
    onSurfaceVariant = Color(0xFF3F4753),
    outline = Color(0xFF6F7884),
    outlineVariant = Color(0xFFBEC7D5),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C3138),
    inverseOnSurface = Color(0xFFECF1FA),
    inversePrimary = Color(0xFFAEC6FE),
    surfaceDim = Color(0xFFD5DAE3),
    surfaceBright = Color(0xFFF6FAFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF4FD),
    surfaceContainer = Color(0xFFE9EEF7),
    surfaceContainerHigh = Color(0xFFE3E9F2),
    surfaceContainerHighest = Color(0xFFDDE3EC)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4D8DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1D3F8F),
    onPrimaryContainer = Color(0xFFDBE6FF),
    secondary = Color(0xFFBBC6E4),
    onSecondary = Color(0xFF253047),
    secondaryContainer = Color(0xFF3B475F),
    onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFFEBB7D9),
    onTertiary = Color(0xFF4B223F),
    tertiaryContainer = Color(0xFF643856),
    onTertiaryContainer = Color(0xFFFFD7F0),
    error = Color(0xFFFEB4A6),
    onError = Color(0xFF690001),
    errorContainer = Color(0xFF93010C),
    onErrorContainer = Color(0xFFFFDBD3),
    background = Color(0xFF0A0E17),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF0A0E17),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1F283A),
    onSurfaceVariant = Color(0xFFA9B0C3),
    outline = Color(0xFF6B7390),
    outlineVariant = Color(0xFF2A3247),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDDE3EC),
    inverseOnSurface = Color(0xFF2C3138),
    inversePrimary = Color(0xFF1F6FE0),
    surfaceDim = Color(0xFF0A0E17),
    surfaceBright = Color(0xFF262F44),
    surfaceContainerLowest = Color(0xFF070A11),
    surfaceContainerLow = Color(0xFF0E131D),
    surfaceContainer = Color(0xFF121826),
    surfaceContainerHigh = Color(0xFF182030),
    surfaceContainerHighest = Color(0xFF1F283A)
)

/**
 * App theme: stock Material 3 (colour scheme, typography, shapes).
 *
 * On Android 12+ the scheme follows the wallpaper (Material You) unless the user
 * turned "Dynamic colour" off in Settings.
 */
@Composable
fun PrimeBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}
