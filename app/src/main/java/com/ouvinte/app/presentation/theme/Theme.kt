package com.ouvinte.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF),
    onPrimary = Color(0xFF00356B),
    primaryContainer = Color(0xFF004D99),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF00504C),
    onSecondaryContainer = Color(0xFF9EEFEA),
    tertiary = Color(0xFFCEB4FB),
    onTertiary = Color(0xFF380063),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFFF6B6B),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF00356B),
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF003734),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0D1117),
    surface = Color.White,
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFCBD5E0),
)

@Composable
fun OuvinteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
