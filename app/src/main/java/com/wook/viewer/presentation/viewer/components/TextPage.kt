package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.presentation.theme.LightSurface
import com.wook.viewer.presentation.theme.TextMuted
import com.wook.viewer.presentation.theme.TextPrimary

/**
 * 텍스트 페이지 (HWP/DOCX/PPTX/XLSX/MD/TXT 등 TEXT_ONLY).
 *
 * Pencil 디자인:
 *  - 흰 배경 + dark 텍스트 (가독성)
 *  - SelectionContainer로 길게 눌러 선택, 시스템 메뉴 (복사/공유/모두)
 */
@Composable
fun TextPage(
    text: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightSurface),
        contentAlignment = Alignment.Center
    ) {
        when (text) {
            null -> CircularProgressIndicator()
            "" -> Text(
                text = "(빈 페이지)",
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            else -> SelectionContainer(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = text,
                    color = TextPrimary,
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
