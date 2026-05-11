package com.wook.viewer.render.pptx

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageElement
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.render.text.TextPage
import com.wook.viewer.render.text.TextPageRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PPTX 렌더러 — 슬라이드별 도형 좌표 보존.
 *
 * 한 슬라이드 = 한 페이지. getPageElements 가 single-element list 반환:
 *   - PageElement.PositionedLayout(슬라이드 크기, 도형 리스트)
 *
 * UI(PositionedSlideView)가 컨테이너 폭에 맞춰 비율 변환 + 절대 배치.
 */
@Singleton
class PptxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.PPTX)

    private class Handle(
        override val uri: Uri,
        val slides: List<PptxTextExtractor.SlideContent>
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".ppt", ignoreCase = true) &&
            !name.endsWith(".pptx", ignoreCase = true)
        ) {
            throw DocumentError.UnsupportedVariant("ppt (구형 바이너리)")
        }

        val slides = try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for $uri" }
                PptxTextExtractor.extract(input)
            }
        } catch (e: PptxFormatException) {
            Timber.w(e, "PPTX 형식 아님")
            throw DocumentError.UnsupportedVariant("ppt (구형 바이너리)", e)
        } catch (t: Throwable) {
            Timber.e(t, "PPTX 텍스트 추출 실패")
            throw classifyPptxError(t)
        }

        Timber.d("PPTX 열기: slides=${slides.size}, totalShapes=${slides.sumOf { it.shapes.size }}")
        Handle(uri, slides)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int =
        (handle as Handle).slides.size

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        PageSize(595f, 842f)

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.Default) {
        val h = handle as Handle
        val text = h.slides.getOrNull(index)?.text ?: "(잘못된 페이지 인덱스: $index)"
        TextPageRenderer.render(TextPage(text), targetWidthPx, index)
    }

    /** 검색용 평탄 텍스트. */
    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return h.slides.getOrNull(index)?.text
    }

    /** 폴백용 평탄 이미지 (UI가 PositionedLayout 처리 못 할 때). */
    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return h.slides.getOrNull(index)?.images ?: emptyList()
    }

    override suspend fun getPageElements(
        handle: DocumentHandle,
        index: Int
    ): List<PageElement>? {
        val h = handle as Handle
        val slide = h.slides.getOrNull(index) ?: return null
        return listOf(
            PageElement.PositionedLayout(
                widthEmu = slide.slideWidthEmu,
                heightEmu = slide.slideHeightEmu,
                shapes = slide.shapes
            )
        )
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        h.slides.flatMap { it.images }.forEach { runCatching { it.recycle() } }
    }

    private fun classifyPptxError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "encrypt" in msg || "password" in msg -> DocumentError.PasswordProtected(cause = t)
            t is java.io.IOException -> DocumentError.IoError(t)
            else -> DocumentError.Corrupted(t)
        }
    }
}
