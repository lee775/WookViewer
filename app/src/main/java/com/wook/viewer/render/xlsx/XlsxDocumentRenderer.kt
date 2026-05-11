package com.wook.viewer.render.xlsx

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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XLSX 렌더러 — 1 시트 = 1 페이지.
 *
 * 변경(v0.6.4):
 *   - 시트를 텍스트 길이 기준으로 페이지화하지 않고 시트 그대로 보관
 *   - getSectionLabels 로 시트 이름 노출 → UI가 탭으로 보여줄 수 있음
 *   - 시트별 이미지는 sheet rels → drawing rels 추적으로 매핑
 */
@Singleton
class XlsxDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.XLSX)

    private class Handle(
        override val uri: Uri,
        val sheets: List<XlsxTextExtractor.Sheet>
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

        Timber.d("XLSX 열기 완료: sheets=${sheets.size}, totalImages=${sheets.sumOf { it.images.size }}")
        Handle(uri, sheets)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int =
        (handle as Handle).sheets.size

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        PageSize(595f, 842f)

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.Default) {
        val h = handle as Handle
        val sheet = h.sheets.getOrElse(index) {
            return@withContext TextPageRenderer.render(
                TextPage("(잘못된 시트 인덱스: $index)"),
                targetWidthPx,
                index
            )
        }
        val body = if (sheet.text.isBlank()) "(빈 시트)" else sheet.text
        TextPageRenderer.render(TextPage("[${sheet.name}]\n$body"), targetWidthPx, index)
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        val sheet = h.sheets.getOrNull(index) ?: return null
        val body = if (sheet.text.isBlank()) "(빈 시트)" else sheet.text
        return "[${sheet.name}]\n\n$body"
    }

    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return h.sheets.getOrNull(index)?.images ?: emptyList()
    }

    override suspend fun getSectionLabels(handle: DocumentHandle): List<String>? {
        val h = handle as Handle
        return h.sheets.map { it.name }
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        h.sheets.flatMap { it.images }.forEach { runCatching { it.recycle() } }
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
