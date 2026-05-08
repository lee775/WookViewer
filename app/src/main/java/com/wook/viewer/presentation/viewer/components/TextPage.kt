package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 텍스트 페이지 (HWP/DOCX/PPTX/XLSX/MD/TXT 등 TEXT_ONLY).
 *
 * v0.5.1: 검색 매치 하이라이트 지원.
 *  - matches: 모든 매치 범위 (노란색 배경)
 *  - activeMatch: 현재 활성 매치 (주황색 배경, 더 강조)
 */
@Composable
fun TextPage(
    text: String?,
    matches: List<IntRange> = emptyList(),
    activeMatch: IntRange? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            null -> CircularProgressIndicator()
            "" -> Text(
                text = "(빈 페이지)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            else -> {
                val rendered = if (matches.isEmpty()) {
                    AnnotatedString(text)
                } else {
                    highlightMatches(text, matches, activeMatch)
                }
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = rendered,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    )
                }
            }
        }
    }
}

private val MatchBg = Color(0xFFFFF59D)       // 부드러운 노란색
private val MatchActiveBg = Color(0xFFFFB74D) // 주황색 (현재 매치)

private fun highlightMatches(
    text: String,
    matches: List<IntRange>,
    activeMatch: IntRange?
): AnnotatedString = buildAnnotatedString {
    append(text)
    matches.forEach { range ->
        val start = range.first.coerceAtLeast(0)
        val endExclusive = (range.last + 1).coerceAtMost(text.length)
        if (start < endExclusive) {
            val isActive = activeMatch != null &&
                activeMatch.first == range.first &&
                activeMatch.last == range.last
            addStyle(
                SpanStyle(background = if (isActive) MatchActiveBg else MatchBg),
                start, endExclusive
            )
        }
    }
}
