package com.wook.viewer.render.pptx

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
 * PPTX 렌더러 (v0.4, TEXT_ONLY 충실도).
 *
 * 매핑 전략: **슬라이드 1개 = 페이지 1개** (PPTX의 자연스러운 구조).
 * 거대 슬라이드(>1500자)는 TextPaginator로 추가 분할.
 *
 * `.ppt` 구형 OLE 바이너리는 명시적으로 거부.
 *
 * 외부 라이브러리 0개 — APK 사이즈 영향 없음 (DOCX와 동일 전략).
 *
 * 한계 (v0.2 안내 배너로 사용자에게 노출):
 *   - 표는 셀 텍스트만 (탭 구분), 구조 X
 *   - 이미지/도형/SmartArt 무시
 *   - 발표자 노트 무시
 *   - 애니메이션/전환 미지원
 *   - 슬라이드 마스터의 배경/제목 형식 미반영
 *   - 슬라이드 순서는 **파일명 기준** — 사용자가 PowerPoint에서 재배치한 경우 표시 순서와 다를 수 있음
 */
@Singleton
class PptxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.PPTX)

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        // .ppt 구형 바이너리 사전 분기
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".ppt", ignoreCase = true) &&
            !name.endsWith(".pptx", ignoreCase = true)
        ) {
            throw DocumentError.UnsupportedVariant("ppt (구형 바이너리)")
        }

        val slideTexts = try {
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

        val pages = slideTexts.flatMap { slideText ->
            val content = slideText.ifBlank { "(빈 슬라이드)" }
            if (content.length <= TextPaginator.APPROX_CHARS_PER_PAGE) {
                listOf(TextPage(content))
            } else {
                // 드문 케이스: 매우 큰 슬라이드 → 여러 페이지로 분할
                TextPaginator.paginate(content)
            }
        }
        Timber.d("PPTX 열기 완료: slides=${slideTexts.size}, pages=${pages.size}")
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

    private fun classifyPptxError(t: Throwable): DocumentError {
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
