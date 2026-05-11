package com.wook.viewer.render.pptx

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import com.wook.viewer.render.text.TextPage
import com.wook.viewer.render.text.TextPageRenderer
import com.wook.viewer.render.text.TextPaginator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PptxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.PPTX)

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>,
        /** 페이지 인덱스 → 슬라이드의 이미지 리스트. 거대 슬라이드 분할 시 추가 페이지는 빈 리스트. */
        val pageImages: List<List<Bitmap>>
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

        val pages = mutableListOf<TextPage>()
        val pageImages = mutableListOf<List<Bitmap>>()

        slides.forEach { slide ->
            val content = slide.text.ifBlank { "(빈 슬라이드)" }
            if (content.length <= TextPaginator.APPROX_CHARS_PER_PAGE) {
                pages += TextPage(content)
                pageImages += slide.images
            } else {
                val split = TextPaginator.paginate(content)
                pages += split
                // 첫 분할에만 이미지 표시, 이후 분할은 빈 리스트
                split.forEachIndexed { idx, _ ->
                    pageImages += if (idx == 0) slide.images else emptyList()
                }
            }
        }
        Timber.d("PPTX 열기 완료: slides=${slides.size}, pages=${pages.size}, totalImages=${slides.sumOf { it.images.size }}")
        Handle(uri, pages, pageImages)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int =
        (handle as Handle).pages.size

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        PageSize(595f, 842f)

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.Default) {
        val h = handle as Handle
        val page = h.pages.getOrElse(index) { TextPage("(잘못된 페이지 인덱스: $index)") }
        TextPageRenderer.render(page, targetWidthPx, index)
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return h.pages.getOrNull(index)?.text
    }

    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return h.pageImages.getOrNull(index) ?: emptyList()
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        h.pageImages.flatten().forEach { runCatching { it.recycle() } }
    }

    private fun classifyPptxError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "encrypt" in msg || "password" in msg -> DocumentError.PasswordProtected(t)
            t is java.io.IOException -> DocumentError.IoError(t)
            else -> DocumentError.Corrupted(t)
        }
    }
}
