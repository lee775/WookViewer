package com.wook.viewer.presentation.viewer.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 페이지 썸네일 그리드.
 *
 * 각 셀은 [loadBitmap](pageIndex, targetWidthPx)를 비동기로 호출해 비트맵을 받는다.
 * LazyVerticalGrid가 화면에 보이지 않는 셀은 dispose하므로 큰 문서에서도 메모리 안전.
 *
 * @param currentIndex 현재 보던 페이지 — 강조 테두리 표시
 * @param loadBitmap   suspend (index, widthPx) → Bitmap? — null이면 placeholder 유지
 * @param onSelect     셀 클릭 시 호출 (페이지 인덱스 전달)
 */
@Composable
fun ThumbnailGrid(
    pageCount: Int,
    currentIndex: Int,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = (0 until pageCount).toList(), key = { it }) { index ->
            ThumbnailCell(
                index = index,
                isCurrent = index == currentIndex,
                loadBitmap = loadBitmap,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun ThumbnailCell(
    index: Int,
    isCurrent: Boolean,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    // 셀 폭 약 120dp → 그 정도 픽셀로 렌더 (작은 PDF 비트맵)
    val renderWidthPx = with(density) { 200.dp.roundToPx() }

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = index) {
        value = runCatching { loadBitmap(index, renderWidthPx) }.getOrNull()
    }

    val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),  // A4 비율 근사
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 11.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
