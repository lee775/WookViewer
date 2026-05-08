package com.wook.viewer.render.xlsx

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
 * XLSX 렌더러 (TEXT_ONLY 충실도).
 *
 * 시트 1개 = 페이지 1개 (PPTX와 같은 매핑). 거대 시트는 TextPaginator 폴백.
 *
 * 한계 (배너로 안내):
 *   - 표는 셀 텍스트만 (탭 구분), 그리드 모양 X
 *   - 셀 서식(굵게/색상/병합) 무시
 *   - 차트/이미지/도형 무시
 *   - 수식은 결과값만 표시 (수식 자체 미노출)
 *   - 시트 순서는 파일명(sheet1, sheet2, ...) 기준
 */
@Singleton
class XlsxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.XLSX)

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".xls", ignoreCase = true) &&
            !name.endsWith(".xlsx", ignoreCase = true)
        ) {
            throw DocumentError.UnsupportedVariant("xls (구형 바이너리)")
        }

        val sheets = try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for $uri" }
                XlsxTextExtractor.extract(input)
            }
        } catch (e: XlsxFormatException) {
            Timber.w(e, "XLSX 형식 아님")
            throw DocumentError.UnsupportedVariant("xls (구형 바이너리)", e)
        } catch (t: Throwable) {
            Timber.e(t, "XLSX 추출 실패")
            throw classifyError(t)
        }

        val pages = sheets.flatMap { sheet ->
            val header = "[${sheet.name}]\n"
            val content = if (sheet.text.isBlank()) "(빈 시트)" else sheet.text
            val full = header + content
            if (full.length <= TextPaginator.APPROX_CHARS_PER_PAGE) {
                listOf(TextPage(full))
            } else {
                TextPaginator.paginate(full)
            }
        }
        Timber.d("XLSX 열기 완료: sheets=${sheets.size}, pages=${pages.size}")
        Handle(uri, pages)
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

    override suspend fun close(handle: DocumentHandle) {
        // 리소스 없음
    }

    private fun classifyError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "encrypt" in msg || "password" in msg -> DocumentError.PasswordProtected(t)
            t is java.io.IOException -> DocumentError.IoError(t)
            else -> DocumentError.Corrupted(t)
        }
    }
}
