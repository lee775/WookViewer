package com.wook.viewer.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.wook.viewer.domain.model.TextScale
import com.wook.viewer.domain.model.ThemeMode

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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: TextScale = TextScale.MEDIUM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * textScale.multiplier
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
