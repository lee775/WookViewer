package com.wook.viewer.presentation.theme

import androidx.compose.ui.graphics.Color
import com.wook.viewer.domain.model.DocumentFormat

/**
 * 포맷 칩 — 파일 카드의 색상 사각 라벨.
 * 각 포맷마다 부드러운 파스텔 배경 + 진한 텍스트 색.
 */
data class FormatStyle(
    val background: Color,
    val foreground: Color,
    val label: String
)

fun DocumentFormat.style(): FormatStyle = when (this) {
    DocumentFormat.PDF -> FormatStyle(Color(0xFFFFE5E5), Color(0xFFE04848), "PDF")
    DocumentFormat.DOCX -> FormatStyle(Color(0xFFE5F0FF), Color(0xFF3B7DD8), "DOCX")
    DocumentFormat.PPTX -> FormatStyle(Color(0xFFFFF1E0), Color(0xFFD87A1A), "PPTX")
    DocumentFormat.XLSX -> FormatStyle(Color(0xFFE0F5F0), Color(0xFF1A8B72), "XLSX")
    DocumentFormat.HWP -> FormatStyle(Color(0xFFE8F4EE), Color(0xFF3F8755), "한글")
    DocumentFormat.MARKDOWN -> FormatStyle(Color(0xFFF0E5FF), Color(0xFF7B5BC9), "MD")
    DocumentFormat.PLAIN_TEXT -> FormatStyle(Color(0xFFF0F0F0), Color(0xFF6B7280), "TXT")
    DocumentFormat.IMAGE -> FormatStyle(Color(0xFFFFE9F0), Color(0xFFD14F8A), "IMG")
}
