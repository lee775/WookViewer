package com.wook.viewer.domain.model

/**
 * 포맷별 렌더 충실도.
 *
 * 사용자에게 "이 포맷은 원본 그대로 보이는가, 단순화되는가"를 알리는 데 사용.
 * UI는 이 값을 보고 안내 배너 노출 여부를 결정한다.
 */
enum class RenderingFidelity {
    /** 원본 레이아웃과 거의 동일하게 표시 (예: PDF). */
    FULL,

    /**
     * 텍스트는 보이지만 표/이미지/서식이 단순화 또는 누락됨.
     * 예: 현재 PoC 단계의 HWP/HWPX.
     */
    TEXT_ONLY
}
