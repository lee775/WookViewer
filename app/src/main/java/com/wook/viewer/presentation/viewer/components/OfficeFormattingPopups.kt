package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/** 도구바에서 자주 쓰는 pt 단위 글꼴 크기. */
private val FONT_SIZES = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 36f, 48f, 72f)

/** 글꼴 미리보기 샘플 — 한글·영문·숫자 한 줄. */
private const val PREVIEW_SAMPLE = "가나다 AaBb 123"

/**
 * 미리보기 텍스트 — 해당 글꼴로 렌더링.
 * Compose 의 platform Typeface API 가 우리 버전에서 public 이 아니라
 * AndroidView + TextView 로 우회. Android Typeface.create 가 매핑 못하면
 * 시스템 폴백 폰트 사용 (이름만 표시되고 모양은 기본).
 */
@Composable
private fun FontPreviewText(fontName: String, textColorArgb: Int) {
    AndroidView(
        factory = { ctx ->
            android.widget.TextView(ctx).apply {
                text = PREVIEW_SAMPLE
                textSize = 14f
                setTextColor(textColorArgb)
                typeface = android.graphics.Typeface.create(
                    fontName, android.graphics.Typeface.NORMAL
                )
            }
        },
        update = { tv ->
            tv.typeface = android.graphics.Typeface.create(
                fontName, android.graphics.Typeface.NORMAL
            )
            tv.setTextColor(textColorArgb)
        }
    )
}

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
 * 글꼴 종류 드롭다운.
 * [fonts] 는 LOK 에서 조회한 사용 가능 글꼴 목록 (없으면 기본 폴백).
 * 목록이 길 수 있어 최대 높이 제한 + 스크롤.
 */
@Composable
fun FontNameDropdown(
    expanded: Boolean,
    fonts: List<String>,
    onDismissRequest: () -> Unit,
    onSelect: (fontName: String) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.heightIn(max = 360.dp)
    ) {
        if (fonts.isEmpty()) {
            DropdownMenuItem(
                text = { Text("글꼴 목록 로딩 중…", fontSize = 13.sp) },
                onClick = {},
                enabled = false
            )
        } else {
            val previewColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
            fonts.forEach { name ->
                DropdownMenuItem(
                    text = {
                        Column {
                            // 글꼴 이름 — 작은 라벨 (기본 글꼴)
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            // 미리보기 — 해당 글꼴로 렌더링 (AndroidView/TextView 우회)
                            FontPreviewText(
                                fontName = name,
                                textColorArgb = previewColorArgb
                            )
                        }
                    },
                    onClick = {
                        onSelect(name)
                        onDismissRequest()
                    }
                )
            }
        }
    }
}

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
                        ColorSwatch(entry = entry, mode = mode, onClick = {
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
private fun ColorSwatch(entry: PaletteEntry, mode: String, onClick: () -> Unit) {
    val isAuto = entry.rgb == -1L
    val color = if (isAuto) Color.Transparent
    else Color(0xFF000000 or (entry.rgb and 0xFFFFFFL))

    // font 모드: 글자에 색을 입혀 surfaceVariant 배경에 표시
    // back 모드: 글자는 검정, 배경에 색을 입힘 (실제 형광펜 효과와 동일)
    val tileBg = when {
        isAuto -> MaterialTheme.colorScheme.surface
        mode == "back" -> color
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isAuto -> MaterialTheme.colorScheme.onSurface
        mode == "back" -> Color.Black
        else -> color
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
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tileBg)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isAuto) "자동" else "가",
                fontSize = if (isAuto) 10.sp else 20.sp,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            entry.label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal
        )
    }
}
