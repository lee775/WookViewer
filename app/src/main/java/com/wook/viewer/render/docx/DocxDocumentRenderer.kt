package com.wook.viewer.render.docx

import android.content.Context
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

/**
 * DOCX 렌더러 (v0.3, TEXT_ONLY 충실도).
 *
 * 구현 전략:
 *   - .docx (ZIP+XML) → DocxTextExtractor로 본문 텍스트 추출
 *   - .doc (구형 OLE 바이너리) → UnsupportedVariant 에러
 *   - 텍스트는 공통 TextPaginator로 페이지 분할, TextPageRenderer로 Bitmap
 *
 * 외부 라이브러리 0개 — APK 사이즈 영향 없음.
 *
 * 한계 (v0.2 안내 배너로 사용자에게 노출):
 *   - 표는 셀 텍스트만 (탭 구분), 구조 X
 *   - 이미지/도형/SmartArt 무시
 *   - 머리말/꼬리말/댓글/트랙체인지 무시
 *   - 글꼴/색상/정렬 단순화
 */
@Singleton
class DocxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormat: DocumentFormat = DocumentFormat.DOCX

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        // .doc 구형 바이너리는 사전에 분기
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".doc", ignoreCase = true) &&
            !name.endsWith(".docx", ignoreCase = true)
        ) {
            throw DocumentError.UnsupportedVariant("doc (구형 바이너리)")
        }

        val text = try {
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

        val pages = TextPaginator.paginate(text)
        Timber.d("DOCX 열기 완료: pages=${pages.size}, chars=${text.length}")
        Handle(uri, pages)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int =
        (handle as Handle).pages.size

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        PageSize(595f, 842f)  // A4

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

    override suspend fun close(handle: DocumentHandle) {
        // 리소스 없음 (스트리밍 추출, 임시 파일 없음)
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
