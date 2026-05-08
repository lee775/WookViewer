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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 텍스트 페이지 — 길게 누르고 드래그해서 선택, 시스템 메뉴로 복사 가능.
 * SelectionContainer가 안드로이드 기본 텍스트 선택 UX를 제공.
 *
 * HWP/DOCX/PPTX 등 TEXT_ONLY 포맷용.
 */
@Composable
fun TextPage(
    text: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF202225)),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            null -> CircularProgressIndicator()
            "" -> Text(
                text = "(빈 페이지)",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
            else -> SelectionContainer(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                )
            }
        }
    }
}
