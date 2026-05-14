package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Office 편집 모드에서 표시되는 서식 도구바.
 * 각 버튼은 LOK `.uno:*` 명령을 전송 — LOK 가 현재 selection 에 적용.
 *
 * 화면이 좁으면 가로 스크롤로 더 많은 버튼 노출.
 * 복사/붙여넣기는 Android clipboard 와 브릿지 (onCopy / onPaste 콜백).
 */
@Composable
fun OfficeFormattingToolbar(
    onUnoCommand: (String) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    fonts: List<String>,
    onFontName: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onFontColor: (Long) -> Unit,
    onBackColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    var fontMenuExpanded by remember { mutableStateOf(false) }
    var sizeMenuExpanded by remember { mutableStateOf(false) }
    var fontColorMenuExpanded by remember { mutableStateOf(false) }
    var backColorMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToolButton(Icons.Filled.Undo, "실행 취소") { onUnoCommand(".uno:Undo") }
        ToolButton(Icons.Filled.Redo, "다시 실행") { onUnoCommand(".uno:Redo") }
        Spacer8()
        ToolButton(Icons.Filled.ContentCopy, "복사") { onCopy() }
        ToolButton(Icons.Filled.ContentPaste, "붙여넣기") { onPaste() }
        Spacer8()
        // 글꼴 종류 드롭다운
        Box {
            ToolButton(Icons.Filled.FontDownload, "글꼴 종류") { fontMenuExpanded = true }
            FontNameDropdown(
                expanded = fontMenuExpanded,
                fonts = fonts,
                onDismissRequest = { fontMenuExpanded = false },
                onSelect = onFontName
            )
        }
        // 글꼴 크기 드롭다운 — 버튼 자체가 anchor
        Box {
            ToolButton(Icons.Filled.FormatSize, "글꼴 크기") { sizeMenuExpanded = true }
            FontSizeDropdown(
                expanded = sizeMenuExpanded,
                onDismissRequest = { sizeMenuExpanded = false },
                onSelect = onFontSize
            )
        }
        // 글자색
        Box {
            ToolButton(Icons.Filled.FormatColorText, "글자색") { fontColorMenuExpanded = true }
            ColorPaletteDropdown(
                expanded = fontColorMenuExpanded,
                mode = "font",
                onDismissRequest = { fontColorMenuExpanded = false },
                onSelect = onFontColor
            )
        }
        // 배경색(형광펜)
        Box {
            ToolButton(Icons.Filled.FormatColorFill, "형광펜") { backColorMenuExpanded = true }
            ColorPaletteDropdown(
                expanded = backColorMenuExpanded,
                mode = "back",
                onDismissRequest = { backColorMenuExpanded = false },
                onSelect = onBackColor
            )
        }
        Spacer8()
        ToolButton(Icons.Filled.FormatBold, "굵게") { onUnoCommand(".uno:Bold") }
        ToolButton(Icons.Filled.FormatItalic, "기울임") { onUnoCommand(".uno:Italic") }
        ToolButton(Icons.Filled.FormatUnderlined, "밑줄") { onUnoCommand(".uno:Underline") }
        Spacer8()
        ToolButton(Icons.Filled.FormatAlignLeft, "왼쪽 정렬") { onUnoCommand(".uno:LeftPara") }
        ToolButton(Icons.Filled.FormatAlignCenter, "가운데 정렬") { onUnoCommand(".uno:CenterPara") }
        ToolButton(Icons.Filled.FormatAlignRight, "오른쪽 정렬") { onUnoCommand(".uno:RightPara") }
        ToolButton(Icons.Filled.FormatAlignJustify, "양쪽 정렬") { onUnoCommand(".uno:JustifyPara") }
        Spacer8()
        ToolButton(Icons.Filled.FormatListBulleted, "글머리 기호") {
            onUnoCommand(".uno:DefaultBullet")
        }
        ToolButton(Icons.Filled.FormatListNumbered, "번호 매기기") {
            onUnoCommand(".uno:DefaultNumbering")
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
}
