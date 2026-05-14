package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 도구바에서 자주 쓰는 pt 단위 글꼴 크기. */
private val FONT_SIZES = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f, 72f)

/** 기본 팔레트 — 자동 + 8 색상. 3 행 × 3 열 격자. */
private data class PaletteEntry(val label: String, val rgb: Long)

private val FONT_COLOR_PALETTE = listOf(
    PaletteEntry("자동", -1L),
    PaletteEntry("검정", 0x000000L),
    PaletteEntry("흰색", 0xFFFFFFL),
    PaletteEntry("빨강", 0xE53935L),
    PaletteEntry("주황", 0xFB8C00L),
    PaletteEntry("노랑", 0xFDD835L),
    PaletteEntry("초록", 0x43A047L),
    PaletteEntry("파랑", 0x1E88E5L),
    PaletteEntry("보라", 0x8E24AAL),
)

/** 형광펜 — "없음" + 5 형광펜 색상. */
private val BACK_COLOR_PALETTE = listOf(
    PaletteEntry("없음", -1L),
    PaletteEntry("노랑", 0xFFEB3BL),
    PaletteEntry("초록", 0xC5E1A5L),
    PaletteEntry("파랑", 0x90CAF9L),
    PaletteEntry("분홍", 0xF8BBD0L),
    PaletteEntry("주황", 0xFFCCBCL),
)

/**
 * 글꼴 크기 드롭다운.
 * 외부에서 `expanded` 제어 — 도구바 버튼이 [onDismissRequest] 와 함께 전달.
 */
@Composable
fun FontSizeDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSelect: (sizePt: Float) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        FONT_SIZES.forEach { size ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = "${if (size == size.toInt().toFloat()) size.toInt() else size} pt",
                        fontSize = 14.sp
                    )
                },
                onClick = {
                    onSelect(size)
                    onDismissRequest()
                }
            )
        }
    }
}

/**
 * 색상 팔레트 드롭다운.
 * @param mode "font" 면 글자색 팔레트, "back" 이면 배경색 팔레트.
 */
@Composable
fun ColorPaletteDropdown(
    expanded: Boolean,
    mode: String,
    onDismissRequest: () -> Unit,
    onSelect: (rgb: Long) -> Unit
) {
    val palette = if (mode == "back") BACK_COLOR_PALETTE else FONT_COLOR_PALETTE
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 180.dp).padding(8.dp)
    ) {
        // 3 열 격자로 표시
        val chunks = palette.chunked(3)
        Column {
            chunks.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { entry ->
                        ColorSwatch(entry = entry, onClick = {
                            onSelect(entry.rgb)
                            onDismissRequest()
                        })
                    }
                    repeat(3 - row.size) {
                        Box(modifier = Modifier.size(56.dp))  // 빈 칸 채움
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(entry: PaletteEntry, onClick: () -> Unit) {
    val isAuto = entry.rgb == -1L
    val color = if (isAuto) Color.Transparent
    else Color(0xFF000000 or entry.rgb).copy(alpha = 1f).let {
        // Long → ARGB Color (0xFFRRGGBB)
        Color(0xFF000000 or (entry.rgb and 0xFFFFFFL))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isAuto) MaterialTheme.colorScheme.surface else color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isAuto) {
                Text("자동", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            entry.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal
        )
    }
}
