package io.github.mateuxlucax.healthconnectbridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback for Android 11 and lower, derived from the app icon: navy, Grafana orange, yellow.
private val LightColors = lightColorScheme(
    primary = Color(0xFFA53D0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF390C00),
    secondary = Color(0xFF4B5568),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2F3),
    onSecondaryContainer = Color(0xFF1E2430),
    tertiary = Color(0xFF6B5E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE26E),
    onTertiaryContainer = Color(0xFF201C00),
    background = Color(0xFFF9F9FC),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF9F9FC),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F7),
    surfaceContainer = Color(0xFFEDEDF2),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    surfaceContainerHighest = Color(0xFFE1E2E7),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59C),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF822A00),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFC0C6D8),
    onSecondary = Color(0xFF2A303D),
    secondaryContainer = Color(0xFF3A4252),
    onSecondaryContainer = Color(0xFFDCE2F3),
    tertiary = Color(0xFFFBCA0A),
    onTertiary = Color(0xFF383000),
    tertiaryContainer = Color(0xFF514700),
    onTertiaryContainer = Color(0xFFFFE26E),
    background = Color(0xFF11141B),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF3F4757),
    onSurfaceVariant = Color(0xFFC3C6D0),
    surfaceContainerLowest = Color(0xFF0C0F15),
    surfaceContainerLow = Color(0xFF191C24),
    surfaceContainer = Color(0xFF1E2430),
    surfaceContainerHigh = Color(0xFF282E3B),
    surfaceContainerHighest = Color(0xFF333946),
    outline = Color(0xFF8D909A),
    outlineVariant = Color(0xFF43474F),
)

/** Material You colors on Android 12+, the icon-derived scheme before that. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
