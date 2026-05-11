package com.wook.viewer.render

import com.wook.viewer.data.lok.LokAvailability
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.repository.AppSettingsRepository
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
 * LOK 오버라이드: 설정 [AppSettings.useLibreOfficeForOffice] = true 이고
 * [LokAvailability] 가 true 면, Office 포맷에 대해 [LokDocumentRenderer] 사용.
 * 둘 중 하나라도 false면 기본 맵핑(ZIP+XML 기반 렌더러)으로 폴백.
 */
@Singleton
class RendererRegistryImpl @Inject constructor(
    renderers: Set<@JvmSuppressWildcards DocumentRenderer>,
    private val lokRenderer: LokDocumentRenderer,
    private val lokAvailability: LokAvailability,
    private val settings: AppSettingsRepository
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
        if (!settings.settings.value.useLibreOfficeForOffice) return false
        return lokAvailability.isAvailable()
    }
}
