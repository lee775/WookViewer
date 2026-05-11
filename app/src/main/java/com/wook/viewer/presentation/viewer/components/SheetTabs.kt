package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 시트(섹션) 탭 — XLSX 시트 선택 등에 사용.
 * 가로 스크롤, 활성 탭은 브랜드 색.
 */
@Composable
fun SheetTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { idx, label ->
            val active = idx == selectedIndex
            val bg = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
