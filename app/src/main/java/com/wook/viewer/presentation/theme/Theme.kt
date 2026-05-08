package com.wook.viewer.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 욱뷰어 v0.5 라이트 컬러스킴 — Pencil 디자인 토큰 매핑.
 *
 * 다크모드는 v0.5에서 미적용 (FileList는 항상 라이트, Bitmap Viewer는 자체 다크 배경 사용).
 * 시스템 다이내믹 컬러도 끔 — 욱뷰어 브랜드 일관성 유지.
 */
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

@Composable
fun WookViewerTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
