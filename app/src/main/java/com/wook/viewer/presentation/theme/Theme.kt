package com.wook.viewer.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandAccent,
    onPrimary = TextOnDark,
    primaryContainer = BrandAccentLight,
    onPrimaryContainer = TextPrimary,
    secondary = BrandAccentDark,
    onSecondary = TextOnDark,
    tertiary = BannerAccent,
    onTertiary = TextOnDark,
    tertiaryContainer = BannerBg,
    onTertiaryContainer = BannerFg,
    background = LightBg,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = LightBorder,
    outlineVariant = LightDivider
)

private val DarkColors = darkColorScheme(
    primary = BrandAccent,
    onPrimary = TextOnDark,
    primaryContainer = BrandAccentDark,
    onPrimaryContainer = BrandAccentLight,
    secondary = BrandAccentLight,
    onSecondary = TextPrimary,
    tertiary = BannerAccent,
    onTertiary = TextOnDark,
    tertiaryContainer = BannerBg,
    onTertiaryContainer = BannerFg,
    background = DarkBg,
    onBackground = TextOnDark,
    surface = DarkSurface,
    onSurface = TextOnDark,
    surfaceVariant = DarkElevated,
    onSurfaceVariant = TextOnDarkMuted,
    outline = DarkElevated,
    outlineVariant = DarkSurface
)

@Composable
fun WookViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
