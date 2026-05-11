package com.wook.viewer.render.docx

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
 * DOCX 렌더러 — 인라인 이미지 위치 보존.
 *
 * Segment 기반 페이지네이션:
 *   - 텍스트 누적 길이 기준 페이지 분할
 *   - 이미지는 자기 위치(텍스트 흐름상 인접한 텍스트)와 함께 그 페이지에 속함
 *
 * 이미지가 없는 일반 DOCX는 기존처럼 텍스트만 페이지화.
 */
@Singleton
class DocxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.DOCX)

    private class Handle(
        override val uri: Uri,
        val pages: List<List<PageElement>>,
        val plainPages: List<TextPage>
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

        val pages = paginateSegments(content.elements)
        val plainPages = pages.map { elems ->
            TextPage(elems.filterIsInstance<PageElement.TextElement>().joinToString("") { it.text })
        }
        Timber.d("DOCX 열기 완료: pages=${pages.size}, totalImages=${content.images.size}")
        Handle(uri, pages, plainPages)
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
        val page = h.plainPages.getOrElse(index) {
            TextPage("(잘못된 페이지 인덱스: $index)")
        }
        TextPageRenderer.render(page, targetWidthPx, index)
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return h.plainPages.getOrNull(index)?.text
    }

    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return h.pages.getOrNull(index)
            ?.filterIsInstance<PageElement.ImageElement>()
            ?.map { it.bitmap }
            ?: emptyList()
    }

    override suspend fun getPageElements(
        handle: DocumentHandle,
        index: Int
    ): List<PageElement>? {
        val h = handle as Handle
        return h.pages.getOrNull(index)
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        h.pages.flatten()
            .filterIsInstance<PageElement.ImageElement>()
            .forEach { runCatching { it.bitmap.recycle() } }
    }

    /** segment 단위로 페이지 분할 — 텍스트 길이 누적 기준, 이미지는 "공짜". */
    private fun paginateSegments(
        elements: List<PageElement>
    ): List<List<PageElement>> {
        if (elements.isEmpty()) return listOf(emptyList())

        val pages = mutableListOf<MutableList<PageElement>>()
        var current = mutableListOf<PageElement>()
        var currentChars = 0

        for (elem in elements) {
            when (elem) {
                is PageElement.PositionedLayout -> {
                    // DOCX 페이지에는 슬라이드 형식 요소가 안 나옴 — 안전상 무시
                }
                is PageElement.TextElement -> {
                    val text = elem.text
                    if (text.length > LIMIT) {
                        // 매우 긴 단일 텍스트 → 강제 잘라서 여러 페이지로
                        var remaining = text
                        while (remaining.isNotEmpty()) {
                            val space = LIMIT - currentChars
                            if (space <= 0) {
                                pages += current
                                current = mutableListOf()
                                currentChars = 0
                                continue
                            }
                            val chunk = remaining.take(space)
                            current += PageElement.TextElement(chunk)
                            currentChars += chunk.length
                            remaining = remaining.drop(space)
                            if (currentChars >= LIMIT) {
                                pages += current
                                current = mutableListOf()
                                currentChars = 0
                            }
                        }
                    } else {
                        if (currentChars + text.length > LIMIT && current.isNotEmpty()) {
                            pages += current
                            current = mutableListOf()
                            currentChars = 0
                        }
                        current += elem
                        currentChars += text.length
                    }
                }
                is PageElement.ImageElement -> {
                    current += elem  // 이미지는 길이 카운트 안 함
                }
            }
        }
        if (current.isNotEmpty()) pages += current
        return pages.ifEmpty { listOf(emptyList()) }
    }

    private fun classifyDocxError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "encrypt" in msg || "password" in msg ->
                DocumentError.PasswordProtected(cause = t)
            t is java.io.IOException ->
                DocumentError.IoError(t)
            else ->
                DocumentError.Corrupted(t)
        }
    }

    private companion object {
        const val LIMIT = 1500  // TextPaginator.APPROX_CHARS_PER_PAGE 와 동일
    }
}
