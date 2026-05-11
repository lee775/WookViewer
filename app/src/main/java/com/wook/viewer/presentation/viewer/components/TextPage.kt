package com.wook.viewer.presentation.viewer.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.domain.model.PageElement

/**
 * 텍스트 페이지.
 *
 * 모드:
 *   - elements 비어있지 않음 → 인라인 모드: TextElement/ImageElement 순서대로 렌더
 *   - 비어있고 text 있음     → 평탄 모드: 텍스트 위에 이미지 스트립
 *
 * 두 모드 모두 SelectionContainer 로 텍스트 선택/복사 가능.
 */
@Composable
fun TextPageInline(
    text: String?,
    matches: List<IntRange> = emptyList(),
    activeMatch: IntRange? = null,
    images: List<Bitmap> = emptyList(),
    elements: List<PageElement>? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            !elements.isNullOrEmpty() -> InlineElements(
                elements = elements,
                matches = matches,
                activeMatch = activeMatch
            )
            else -> FlatTextWithImages(
                text = text,
                matches = matches,
                activeMatch = activeMatch,
                images = images
            )
        }
    }
}

@Composable
private fun InlineElements(
    elements: List<PageElement>,
    matches: List<IntRange>,
    activeMatch: IntRange?
) {
    // 매치 위치는 elements의 TextElement들을 이어붙인 평탄 텍스트 기준이라고 가정.
    // 각 TextElement별로 자기 범위를 계산해 해당 부분만 하이라이트.
    var textOffset = 0
    Column(modifier = Modifier.fillMaxWidth()) {
        elements.forEach { elem ->
            when (elem) {
                is PageElement.TextElement -> {
                    val text = elem.text
                    val localStart = textOffset
                    val localEnd = textOffset + text.length
                    val localMatches = matches.mapNotNull { range ->
                        val s = (range.first - localStart).coerceAtLeast(0)
                        val e = (range.last + 1 - localStart).coerceAtMost(text.length)
                        if (s < e && range.last >= localStart && range.first < localEnd)
                            s until e else null
                    }
                    val localActive = activeMatch?.let { range ->
                        val s = (range.first - localStart).coerceAtLeast(0)
                        val e = (range.last + 1 - localStart).coerceAtMost(text.length)
                        if (s < e && range.last >= localStart && range.first < localEnd)
                            s until e else null
                    }
                    if (text.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = if (localMatches.isEmpty()) AnnotatedString(text)
                                else highlight(text, localMatches, localActive),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }
                    textOffset = localEnd
                }
                is PageElement.ImageElement -> {
                    Image(
                        bitmap = elem.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 320.dp)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F3F5))
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatTextWithImages(
    text: String?,
    matches: List<IntRange>,
    activeMatch: IntRange?,
    images: List<Bitmap>
) {
    if (images.isNotEmpty()) {
        ImageStrip(images = images)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
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
                val rendered = if (matches.isEmpty()) AnnotatedString(text)
                else highlight(text, matches, activeMatch)
                SelectionContainer {
                    Text(
                        text = rendered,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageStrip(images: List<Bitmap>) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "이미지 ${images.size}개",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            images.forEachIndexed { idx, bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "이미지 ${idx + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(min = 80.dp, max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F3F5))
                        .padding(2.dp)
                )
            }
        }
    }
}

private val MatchBg = Color(0xFFFFF59D)
private val MatchActiveBg = Color(0xFFFFB74D)

private fun highlight(
    text: String,
    matches: List<IntRange>,
    activeMatch: IntRange?
): AnnotatedString = buildAnnotatedString {
    append(text)
    matches.forEach { range ->
        val s = range.first.coerceAtLeast(0)
        val e = (range.last + 1).coerceAtMost(text.length)
        if (s < e) {
            val isActive = activeMatch != null &&
                activeMatch.first == range.first &&
                activeMatch.last == range.last
            addStyle(SpanStyle(background = if (isActive) MatchActiveBg else MatchBg), s, e)
        }
    }
}
