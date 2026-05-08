package com.wook.viewer.presentation.filelist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.presentation.theme.style
import java.text.DateFormat
import java.util.Date

/**
 * 최근 문서 카드 — 색상 칩 + 파일명 + 부제목.
 *
 * Pencil 디자인 그대로:
 *  - 배경 흰색, 보더 #EEF0F3, 라운드 16
 *  - 패딩 14, 갭 14
 *  - 칩 48x48 라운드 12, 포맷별 파스텔 배경
 *  - 파일명 15sp/600/#111827, 부제목 12sp/#9CA3AF
 */
@Composable
fun FileCard(
    item: RecentDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = item.format.style()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = style.label,
                color = style.foreground,
                fontSize = if (style.label.length >= 4) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "${item.format.displayName} · ${formatRelative(item.lastOpenedAt)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

private fun formatRelative(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        hours < 24 -> "${hours}시간 전"
        days < 7 -> "${days}일 전"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
    }
}
