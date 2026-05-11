package com.wook.viewer.domain.model

/** 다크/라이트/시스템 따름 — 사용자가 설정에서 명시적으로 선택. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 텍스트 배율. 시스템 fontScale 위에 곱해진다.
 * (시스템이 1.0이면 LARGE는 1.15배로 실제 표시)
 */
enum class TextScale(val multiplier: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    XLARGE(1.3f)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: TextScale = TextScale.MEDIUM
)
