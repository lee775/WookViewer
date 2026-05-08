package com.wook.viewer.render

import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.domain.repository.RendererRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RendererRegistryImpl @Inject constructor(
    renderers: Set<@JvmSuppressWildcards DocumentRenderer>
) : RendererRegistry {

    private val byFormat: Map<DocumentFormat, DocumentRenderer> = buildMap {
        renderers.forEach { renderer ->
            renderer.supportedFormats.forEach { format ->
                put(format, renderer)
            }
        }
    }

    override fun rendererFor(format: DocumentFormat): DocumentRenderer? = byFormat[format]
    override fun supportedFormats(): Set<DocumentFormat> = byFormat.keys
}
