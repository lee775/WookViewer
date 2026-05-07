package com.wook.viewer.render.text

/**
 * 평면 텍스트를 논리 페이지로 분할하는 공통 페이지네이터.
 *
 * - 단락(`\n`) 경계를 우선 보존
 * - 한 단락이 페이지 한도보다 길면 강제 분할
 * - 빈 입력은 안내 페이지 1장
 *
 * 진짜 페이지(원본 레이아웃)와 1:1 대응되지 않는다 — 호출 측에서 사용자에게 안내해야 함.
 */
internal object TextPaginator {

    /** 한 페이지 대략 문자 수 (한글/영문 혼합 경험치) */
    const val APPROX_CHARS_PER_PAGE = 1500

    fun paginate(fullText: String): List<TextPage> {
        if (fullText.isBlank()) {
            return listOf(TextPage("(빈 문서이거나 추출 가능한 텍스트가 없습니다)"))
        }

        val paragraphs = fullText.split('\n')
        val pages = mutableListOf<TextPage>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.isNotEmpty() &&
                current.length + para.length + 1 > APPROX_CHARS_PER_PAGE
            ) {
                pages += TextPage(current.toString().trimEnd())
                current.clear()
            }
            if (para.length > APPROX_CHARS_PER_PAGE) {
                if (current.isNotEmpty()) {
                    pages += TextPage(current.toString().trimEnd())
                    current.clear()
                }
                para.chunked(APPROX_CHARS_PER_PAGE).forEach { chunk ->
                    pages += TextPage(chunk)
                }
                continue
            }
            current.append(para).append('\n')
        }
        if (current.isNotEmpty()) {
            pages += TextPage(current.toString().trimEnd())
        }
        return pages.ifEmpty { listOf(TextPage(fullText)) }
    }
}
