package com.wook.viewer.render.hwp

/**
 * HWP는 본질적으로 사전 페이지화된 포맷이 아니다 (실제 페이지는 렌더 시점 레이아웃에 따라 결정).
 * PoC에서는 텍스트 길이 기반의 **논리 페이지** 분할을 사용한다.
 *
 * 한계:
 *  - 실제 한글 페이지와 1:1 대응되지 않는다
 *  - 표/이미지/도형은 텍스트 추출 단계에서 누락
 *  - 절대적 페이지 번호가 의미 없음 (사용자 안내 필요)
 */
internal data class HwpPage(val text: String)

internal object HwpPaginator {

    /** 한 페이지에 담을 대략적인 문자 수 (한글/영문 혼합 기준 경험치). */
    const val APPROX_CHARS_PER_PAGE = 1500

    fun paginate(fullText: String): List<HwpPage> {
        if (fullText.isBlank()) {
            return listOf(HwpPage("(빈 문서이거나 추출 가능한 텍스트가 없습니다)"))
        }

        val paragraphs = fullText.split('\n')
        val pages = mutableListOf<HwpPage>()
        val current = StringBuilder()

        for (para in paragraphs) {
            // 현재 페이지에 새 단락을 추가하면 한도를 넘는다 → 페이지 분리
            if (current.isNotEmpty() &&
                current.length + para.length + 1 > APPROX_CHARS_PER_PAGE
            ) {
                pages += HwpPage(current.toString().trimEnd())
                current.clear()
            }
            // 단락 자체가 한도보다 크면 강제로 잘라서 여러 페이지로
            if (para.length > APPROX_CHARS_PER_PAGE) {
                if (current.isNotEmpty()) {
                    pages += HwpPage(current.toString().trimEnd())
                    current.clear()
                }
                para.chunked(APPROX_CHARS_PER_PAGE).forEach { chunk ->
                    pages += HwpPage(chunk)
                }
                continue
            }
            current.append(para).append('\n')
        }
        if (current.isNotEmpty()) {
            pages += HwpPage(current.toString().trimEnd())
        }
        return pages.ifEmpty { listOf(HwpPage(fullText)) }
    }
}
