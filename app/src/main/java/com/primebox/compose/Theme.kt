package com.primebox.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFF00D98B)
val AccentDark = Color(0xFF00A66B)
val OnAccent = Color(0xFF04120C)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF14251F),
    onPrimaryContainer = Accent,
    secondary = Color(0xFF9AA0A6),
    background = Color(0xFF0A0A0D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121217),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A21),
    onSurfaceVariant = Color(0xFFA0A4AE),
    surfaceContainer = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF1F1F27),
    outline = Color(0xFF2A2A34),
    outlineVariant = Color(0xFF1F1F26),
    error = Color(0xFFFF5C5C)
)

private val LightScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F7EF),
    onPrimaryContainer = Color(0xFF00402A),
    secondary = Color(0xFF5C6270),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0B0F),
    surface = Color(0xFFF5F6F9),
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = Color(0xFFECEEF3),
    onSurfaceVariant = Color(0xFF5C6270),
    surfaceContainer = Color(0xFFF2F3F7),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    outline = Color(0xFFDCE0E8),
    outlineVariant = Color(0xFFE7EAF0),
    error = Color(0xFFB3261E)
)

private val PrimeTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
)

@Composable
fun PrimeBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = PrimeTypography,
        content = content
    )
}
