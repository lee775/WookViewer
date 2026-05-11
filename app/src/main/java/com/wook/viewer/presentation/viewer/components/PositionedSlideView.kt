package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.domain.model.PageElement

/**
 * 절대 좌표 슬라이드 뷰 (PPTX 슬라이드 렌더링).
 *
 * - 컨테이너 폭에 슬라이드 폭을 맞춤
 * - 슬라이드 높이는 폭/슬라이드비율로 계산 (16:9 등)
 * - 각 도형은 EMU → px 변환 후 offset/size 로 절대 배치
 * - 텍스트는 도형 높이의 일정 비율로 폰트 크기 자동 결정 (over-flow는 clip)
 *
 * 텍스트 선택은 슬라이드 전체를 감싸는 SelectionContainer 가 처리.
 */
@Composable
fun PositionedSlideView(
    layout: PageElement.PositionedLayout,
    modifier: Modifier = Modifier
) {
    val slideAspect = layout.widthEmu.toFloat() / layout.heightEmu.toFloat().coerceAtLeast(1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(slideAspect)
            .background(Color.White)
    ) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val pxPerEmu = containerWidthPx / layout.widthEmu.toFloat().coerceAtLeast(1f)

        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                layout.shapes.forEach { shape ->
                    val xDp: Dp = with(density) { (shape.xEmu * pxPerEmu).toDp() }
                    val yDp: Dp = with(density) { (shape.yEmu * pxPerEmu).toDp() }
                    val wDp: Dp = with(density) {
                        (shape.widthEmu.coerceAtLeast(0L) * pxPerEmu).toDp()
                    }
                    val hDp: Dp = with(density) {
                        (shape.heightEmu.coerceAtLeast(0L) * pxPerEmu).toDp()
                    }

                    // 크기 0이면 placeholder가 layout 상속한 경우 — 슬라이드 전체로 그리기 회피
                    val outerW = this@BoxWithConstraints.maxWidth
                    val outerH = this@BoxWithConstraints.maxHeight
                    val finalW = if (wDp > 0.dp) wDp else outerW
                    val finalH = if (hDp > 0.dp) hDp else outerH

                    Box(
                        modifier = Modifier
                            .offset(x = xDp, y = yDp)
                            .size(finalW, finalH)
                            .clipToBounds()
                    ) {
                        if (shape.bitmap != null) {
                            Image(
                                bitmap = shape.bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (!shape.text.isNullOrBlank()) {
                            val fontSizeSp = autoFontSize(finalH, shape.text)
                            Text(
                                text = shape.text,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = fontSizeSp,
                                lineHeight = (fontSizeSp.value * 1.2f).sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (shape.bitmap != null) Color(0x66FFFFFF)
                                        else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 도형 높이를 기준으로 한 행 텍스트가 들어갈 만한 폰트 크기. 매우 단순한 휴리스틱. */
private fun autoFontSize(boxHeight: Dp, text: String) =
    when {
        boxHeight < 30.dp -> 9.sp
        boxHeight < 60.dp -> 12.sp
        boxHeight < 120.dp -> 14.sp
        text.length > 200 -> 12.sp
        else -> 15.sp
    }
