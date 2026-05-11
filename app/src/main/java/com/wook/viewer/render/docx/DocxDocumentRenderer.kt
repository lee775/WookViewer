package com.wook.viewer.render.docx

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
class DocxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.DOCX)

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>,
        val images: List<Bitmap>
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".doc", ignoreCase = true) &&
            !name.endsWith(".docx", ignoreCase = true)
        ) {
            throw DocumentError.UnsupportedVariant("doc (구형 바이너리)")
        }

        val content = try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for $uri" }
                DocxTextExtractor.extract(input)
            }
        } catch (e: DocxFormatException) {
            Timber.w(e, "DOCX 형식 아님")
            throw DocumentError.UnsupportedVariant("doc (구형 바이너리)", e)
        } catch (t: Throwable) {
            Timber.e(t, "DOCX 텍스트 추출 실패")
            throw classifyDocxError(t)
        }

        val pages = TextPaginator.paginate(content.text)
        Timber.d("DOCX 열기 완료: pages=${pages.size}, chars=${content.text.length}, images=${content.images.size}")
        Handle(uri, pages, content.images)
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
        val page = h.pages.getOrElse(index) {
            TextPage("(잘못된 페이지 인덱스: $index)")
        }
        TextPageRenderer.render(page, targetWidthPx, index)
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return h.pages.getOrNull(index)?.text
    }

    /** DOCX는 위치 정보가 복잡해서 모든 이미지를 첫 페이지에 표시. */
    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return if (index == 0) h.images else emptyList()
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        h.images.forEach { runCatching { it.recycle() } }
    }

    private fun classifyDocxError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "encrypt" in msg || "password" in msg ->
                DocumentError.PasswordProtected(t)
            t is java.io.IOException ->
                DocumentError.IoError(t)
            else ->
                DocumentError.Corrupted(t)
        }
    }
}
