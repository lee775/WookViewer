package com.wook.viewer.domain.model

import android.graphics.Bitmap

/**
 * 한 페이지를 구성하는 요소.
 *
 * 텍스트와 이미지가 원본 순서대로 섞여 있을 수 있다 (예: DOCX 인라인 그림).
 * 화면에는 [TextElement] 다음에 [ImageElement] 가 오면 텍스트 아래에 이미지가
 * 자연스럽게 배치된다.
 */
sealed interface PageElement {
    data class TextElement(val text: String) : PageElement
    data class ImageElement(val bitmap: Bitmap) : PageElement
}
