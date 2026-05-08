package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.selection.SelectionContainer
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
 * 텍스트 페이지 — LazyColumn 안에 인라인으로 들어간다.
 *
 * 부모 LazyColumn이 전체 스크롤을 담당하므로 자체 verticalScroll 없음.
 * 대신 wrapContentHeight로 텍스트 길이만큼 슬롯이 늘어남.
 *
 * SelectionContainer는 그대로 — 길게 눌러 선택, 시스템 메뉴 복사.
 */
@Composable
fun TextPageInline(
    text: String?,
    matches: List<IntRange> = emptyList(),
    activeMatch: IntRange? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        contentAlignment = Alignment.TopStart
    ) {
        when (text) {
            null -> Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            "" -> Text(
                text = "(빈 페이지)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            else -> {
                val rendered = if (matches.isEmpty()) {
                    AnnotatedString(text)
                } else {
                    highlightMatches(text, matches, activeMatch)
                }
                SelectionContainer {
                    Text(
                        text = rendered,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}

private val MatchBg = Color(0xFFFFF59D)
private val MatchActiveBg = Color(0xFFFFB74D)

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
