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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.domain.model.HorizontalAlign
import com.wook.viewer.domain.model.PageElement
import com.wook.viewer.domain.model.VerticalAlign

/**
 * 절대 좌표 슬라이드 뷰 (PPTX 슬라이드 렌더링).
 *
 * - 컨테이너 폭에 슬라이드 폭을 맞춤
 * - 슬라이드 높이는 폭/슬라이드비율로 계산 (16:9 등)
 * - 각 도형은 EMU → px 변환 후 offset/size 로 절대 배치
 * - 폰트 크기는 PPTX 원본 sz × 슬라이드 스케일로 계산 (작은 슬라이드도 작게)
 * - 정렬은 hAlign/vAlign 사용 (PPTX a:pPr algn + a:bodyPr anchor)
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
        // PPTX 슬라이드 표준 폭(픽셀에서) 대비 현재 컨테이너 비율 — 폰트 스케일에 사용
        // 슬라이드 디자인 시 96dpi에서 1pt = 1.333px 이지만, 우리는 sp 단위라
        // 슬라이드 → 컨테이너 폭 비율을 직접 곱한다.
        // EMU 914400 = 1 inch, 72pt = 1 inch → 1pt = 12700 EMU
        // 슬라이드의 1pt가 화면에서 차지하는 dp = (12700 EMU * pxPerEmu).toDp() (= 폰트 1pt의 px 폭)
        // sp 변환을 위해 1pt = px의 비율로 sz_sp = sz_pt * (px_per_pt / density)
        // 간단화: 폰트 sp = sz_pt × (slide_render_width_dp / slide_logical_width_pt)
        // slide_logical_width_pt = widthEmu / 12700
        // slide_render_width_dp = maxWidth (Dp)
        val slideLogicalWidthPt = layout.widthEmu / EMU_PER_POINT
        val slideRenderDp = maxWidth.value  // raw dp number
        // 폰트 한 포인트당 sp — fontSize.sp 가 곧 dp 처럼 동작하지만 fontScale 영향을 받음.
        // PPTX 디자인 폰트 크기는 dp 기준으로 슬라이드 폭 비율에 맞춰야 함.
        val pointToSp = slideRenderDp / slideLogicalWidthPt.toFloat()

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

                    // 크기 0이면 placeholder가 layout 상속한 경우 — 슬라이드 전체로 폴백
                    val outerW = this@BoxWithConstraints.maxWidth
                    val outerH = this@BoxWithConstraints.maxHeight
                    val finalW = if (wDp > 0.dp) wDp else outerW
                    val finalH = if (hDp > 0.dp) hDp else outerH

                    Box(
                        modifier = Modifier
                            .offset(x = xDp, y = yDp)
                            .size(finalW, finalH)
                            .clipToBounds(),
                        contentAlignment = composeAlignment(shape.hAlign, shape.vAlign)
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
                            // 폰트 크기: PPTX sz가 있으면 그것을 슬라이드 스케일로 환산.
                            // 없으면 기본 18pt.
                            val sizePt = shape.fontSizePt ?: DEFAULT_FONT_PT
                            val sizeSp = (sizePt * pointToSp).coerceAtLeast(7f)
                            Text(
                                text = shape.text,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = sizeSp.sp,
                                lineHeight = (sizeSp * 1.2f).sp,
                                textAlign = composeTextAlign(shape.hAlign),
                                modifier = Modifier
                                    .fillMaxWidth()
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

private fun composeAlignment(h: HorizontalAlign, v: VerticalAlign): Alignment {
    val horizontal = when (h) {
        HorizontalAlign.LEFT, HorizontalAlign.JUSTIFY -> Alignment.Start
        HorizontalAlign.CENTER -> Alignment.CenterHorizontally
        HorizontalAlign.RIGHT -> Alignment.End
    }
    return when (v) {
        VerticalAlign.TOP -> when (horizontal) {
            Alignment.Start -> Alignment.TopStart
            Alignment.End -> Alignment.TopEnd
            else -> Alignment.TopCenter
        }
        VerticalAlign.CENTER -> when (horizontal) {
            Alignment.Start -> Alignment.CenterStart
            Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
        VerticalAlign.BOTTOM -> when (horizontal) {
            Alignment.Start -> Alignment.BottomStart
            Alignment.End -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }
    }
}

private fun composeTextAlign(h: HorizontalAlign): TextAlign = when (h) {
    HorizontalAlign.LEFT -> TextAlign.Start
    HorizontalAlign.CENTER -> TextAlign.Center
    HorizontalAlign.RIGHT -> TextAlign.End
    HorizontalAlign.JUSTIFY -> TextAlign.Justify
}

private const val EMU_PER_POINT = 12700L  // 914400 EMU / 72 pt
private const val DEFAULT_FONT_PT = 18f  // PPTX 본문 기본
