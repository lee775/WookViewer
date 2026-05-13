package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Office 편집 모드에서 표시되는 서식 도구바.
 * 각 버튼은 LOK `.uno:*` 명령을 전송 — LOK 가 현재 selection 에 적용.
 *
 * 미리 적용된 글꼴/크기 표시는 미구현 — 단순 토글만.
 */
@Composable
fun OfficeFormattingToolbar(
    onUnoCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToolButton(Icons.Filled.Undo, "실행 취소") { onUnoCommand(".uno:Undo") }
        ToolButton(Icons.Filled.Redo, "다시 실행") { onUnoCommand(".uno:Redo") }
        Spacer8()
        ToolButton(Icons.Filled.FormatBold, "굵게") { onUnoCommand(".uno:Bold") }
        ToolButton(Icons.Filled.FormatItalic, "기울임") { onUnoCommand(".uno:Italic") }
        ToolButton(Icons.Filled.FormatUnderlined, "밑줄") { onUnoCommand(".uno:Underline") }
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
