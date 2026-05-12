package com.wook.viewer.render

import com.wook.viewer.data.lok.LokSession
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.domain.repository.RendererRegistry
import com.wook.viewer.render.lok.LOK_SUPPORTED_FORMATS
import com.wook.viewer.render.lok.LokDocumentRenderer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 포맷 → 렌더러 라우터.
 *
 * 일반 흐름: Hilt 가 모은 모든 [DocumentRenderer] 의 supportedFormats 를 합쳐 맵 구성.
 *
 * LOK 오버라이드: [LokSession.isReady] 가 true 이면 Office 포맷에 대해
 * [LokDocumentRenderer] 사용. native lib 부재/init 실패 시 자동으로 기본 매핑 폴백.
 */
@Singleton
class RendererRegistryImpl @Inject constructor(
    renderers: Set<@JvmSuppressWildcards DocumentRenderer>,
    private val lokRenderer: LokDocumentRenderer,
    private val lokSession: LokSession
) : RendererRegistry {

    private val byFormat: Map<DocumentFormat, DocumentRenderer> = buildMap {
        renderers.forEach { renderer ->
            renderer.supportedFormats.forEach { format ->
                put(format, renderer)
            }
        }
    }

    override fun rendererFor(format: DocumentFormat): DocumentRenderer? {
        if (shouldUseLok(format)) return lokRenderer
        return byFormat[format]
    }

    override fun supportedFormats(): Set<DocumentFormat> = byFormat.keys

    private fun shouldUseLok(format: DocumentFormat): Boolean {
        if (format !in LOK_SUPPORTED_FORMATS) return false
        // LOK 초기화 완료 시에만 라우팅. 진행 중/실패 상태면 기본 렌더러 폴백.
        return lokSession.isReady()
    }
}
