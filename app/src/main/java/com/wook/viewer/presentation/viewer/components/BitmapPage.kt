package com.wook.viewer.presentation.viewer.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.wook.viewer.presentation.theme.DarkBg

/**
 * 비트맵 페이지 — LazyColumn 안에 인라인으로 들어간다.
 *
 * 레이아웃:
 *  - 폭: 부모 폭에 채움
 *  - 높이: 비트맵 비율로 (PDF A4면 폭의 1.41배)
 *  - 줌은 graphicsLayer로 시각만 — 레이아웃 슬롯은 고정 (clipToBounds로 넘침 잘라냄)
 *
 * 제스처:
 *  - 두 손가락 핀치 → 줌 (1x..5x), consume
 *  - 줌 1배 + 한 손가락 드래그 → consume 안 함 → LazyColumn이 받아 페이지 흐름 스크롤
 *  - 줌됨 + 한 손가락 드래그 → 페이지 안에서 팬, consume
 *  - 더블 탭 → 1x ↔ 2.5x 토글
 */
@Composable
fun BitmapPageInline(
    bitmap: Bitmap?,
    onWidthChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fallbackAspectRatio: Float = 1f / 1.41f  // A4 폭/높이 비율
) {
    val aspect = bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: fallbackAspectRatio

    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .background(DarkBg)
            .clipToBounds()
            .onSizeChanged { onWidthChanged(it.width) },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_ZOOM
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                // 핀치 — 두 손가락 zoom 변경 → consume
                                if (zoom != 1f) {
                                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    scale = newScale
                                    if (newScale <= 1f) offset = Offset.Zero
                                    event.changes.forEach { it.consume() }
                                }

                                // 줌됨 + pan → 페이지 안에서 팬 (consume)
                                // 줌 1배 + pan → consume 안 함 → LazyColumn 스크롤
                                if (scale > 1f && pan != Offset.Zero) {
                                    offset += pan
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
        }
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
