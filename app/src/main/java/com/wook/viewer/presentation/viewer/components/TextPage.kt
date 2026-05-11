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

/**
 * 텍스트 페이지 — 텍스트 + 임베디드 이미지를 함께 표시.
 *
 * 이미지가 있으면 텍스트 위에 가로 스크롤 가능한 썸네일 리스트(높이 200dp).
 * 텍스트는 SelectionContainer + AnnotatedString 매치 하이라이트 그대로.
 */
@Composable
fun TextPageInline(
    text: String?,
    matches: List<IntRange> = emptyList(),
    activeMatch: IntRange? = null,
    images: List<Bitmap> = emptyList(),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
