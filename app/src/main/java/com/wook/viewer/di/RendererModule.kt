package com.wook.viewer.di

import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.domain.repository.RendererRegistry
import com.wook.viewer.render.RendererRegistryImpl
import com.wook.viewer.render.docx.DocxDocumentRenderer
import com.wook.viewer.render.hwp.HwpDocumentRenderer
import com.wook.viewer.render.pdf.PdfDocumentRenderer
import com.wook.viewer.render.pptx.PptxDocumentRenderer
import com.wook.viewer.render.text.PlainTextDocumentRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 렌더러 멀티바인딩.
 * 새 포맷 렌더러는 @IntoSet 으로 추가만 하면 RendererRegistry가 자동으로 인식한다.
 *
 * v0.4 기준 모든 MVP 포맷 렌더러 등록 완료.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RendererModule {

    @Binds
    @Singleton
    abstract fun bindRegistry(impl: RendererRegistryImpl): RendererRegistry

    @Binds
    @IntoSet
    abstract fun bindPdfRenderer(impl: PdfDocumentRenderer): DocumentRenderer

    @Binds
    @IntoSet
    abstract fun bindHwpRenderer(impl: HwpDocumentRenderer): DocumentRenderer

    @Binds
    @IntoSet
    abstract fun bindDocxRenderer(impl: DocxDocumentRenderer): DocumentRenderer

    @Binds
    @IntoSet
    abstract fun bindPptxRenderer(impl: PptxDocumentRenderer): DocumentRenderer

    /** Markdown + 일반 텍스트 (txt/csv/json/xml/yaml/...) — 단일 렌더러가 다중 포맷 지원. */
    @Binds
    @IntoSet
    abstract fun bindPlainTextRenderer(impl: PlainTextDocumentRenderer): DocumentRenderer
}
