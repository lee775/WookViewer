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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.wook.viewer.presentation.theme.DarkBg

/**
 * 비트맵 페이지 — 핀치 줌 + 더블탭 토글 + 팬.
 *
 * 핵심 제약: VerticalPager 안에 들어가므로 줌 1배일 때 한 손가락 드래그는 pager에
 * 양도해야 한다 (안 그러면 다음 페이지 못 넘어감). transformable 대신
 * awaitEachGesture로 직접 제스처 처리:
 *
 *   - 두 손가락(핀치, zoom != 1f)        → 줌 적용 + consume
 *   - 줌됨(scale > 1f) + 한 손가락 pan   → 이미지 팬 + consume
 *   - 줌 안됨(scale == 1f) + 한 손가락   → consume 안 함 → pager가 처리
 *
 * 페이지가 바뀌면 줌 상태 자동 리셋.
 */
@Composable
fun BitmapPage(
    bitmap: Bitmap?,
    onWidthChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(resetKey) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp)
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

                                // 줌된 상태에서의 pan → consume (이미지 이동)
                                // 줌 1배에서의 pan → consume 안 함 → pager가 받음
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
