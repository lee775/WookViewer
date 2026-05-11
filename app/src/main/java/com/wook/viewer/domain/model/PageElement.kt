package com.wook.viewer.domain.model

import android.graphics.Bitmap

/**
 * 한 페이지를 구성하는 요소.
 */
sealed interface PageElement {
    /** 텍스트 흐름의 한 덩어리. */
    data class TextElement(val text: String) : PageElement

    /** 텍스트 흐름 안에 끼어든 이미지 (DOCX 인라인 그림, HWP 단락 내 그림 등). */
    data class ImageElement(val bitmap: Bitmap) : PageElement

    /**
     * 슬라이드처럼 절대 좌표로 도형들이 배치된 페이지.
     *
     * EMU(English Metric Units) 사용 — 914400 EMU = 1 inch.
     * PPTX 표준 와이드스크린: 12192000 × 6858000 EMU (16:9).
     *
     * UI는 컨테이너 폭에 맞춰 비율로 변환하고, 각 [PositionedShape] 를
     * `offset/size` modifier 로 절대 배치한다.
     */
    data class PositionedLayout(
        val widthEmu: Long,
        val heightEmu: Long,
        val shapes: List<PositionedShape>
    ) : PageElement
}

/**
 * 절대 좌표로 배치되는 도형 한 개.
 *
 * - text 만 있으면 텍스트 박스
 * - bitmap 만 있으면 사진/이미지
 * - 둘 다 있으면 이미지 위에 텍스트 오버레이 (드물지만 가능)
 */
data class PositionedShape(
    val xEmu: Long,
    val yEmu: Long,
    val widthEmu: Long,
    val heightEmu: Long,
    val text: String? = null,
    val bitmap: Bitmap? = null
)
