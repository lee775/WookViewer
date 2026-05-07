package com.wook.viewer.domain.repository

import com.wook.viewer.domain.model.DocumentFormat

/**
 * 포맷 → 렌더러 매핑. 새 포맷 모듈은 이 레지스트리에 등록만 하면 통합 뷰어가 자동으로 사용한다.
 */
interface RendererRegistry {
    fun rendererFor(format: DocumentFormat): DocumentRenderer?
    fun supportedFormats(): Set<DocumentFormat>
}
