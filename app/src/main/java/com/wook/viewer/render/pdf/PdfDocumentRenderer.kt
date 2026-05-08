package com.wook.viewer.render.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF 렌더러.
 *
 * 두 가지 라이브러리 병용:
 *  - 비트맵 렌더: Android 내장 PdfRenderer (가볍고 빠름)
 *  - 텍스트 추출: PdfBox-Android (검색/복사용 텍스트 데이터)
 *
 * 한계:
 *  - 암호화 PDF는 PdfRenderer 단계에서 실패
 *  - 스캔 PDF (이미지만 있는 PDF) 는 텍스트 추출 결과가 비어있음
 *  - PdfBox의 PDDocument를 한 번 열면 모든 페이지 메모리 보유 — 거대 PDF에서 메모리 압박 가능
 */
@Singleton
class PdfDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.PDF)

    private class Handle(
        override val uri: Uri,
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer,
        val pdDocument: PDDocument?,
        val mutex: Mutex = Mutex()
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open file descriptor for $uri")
        val renderer = PdfRenderer(pfd)

        // PdfBox는 별도 InputStream으로 로드 (PdfRenderer가 점유한 ParcelFileDescriptor를 공유 못 함)
        val pdDocument = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input)
            }
        }.onFailure {
            Timber.w(it, "PdfBox 로드 실패 — 텍스트 추출 비활성")
        }.getOrNull()

        Handle(uri, pfd, renderer, pdDocument)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int {
        val h = handle as Handle
        return h.mutex.withLock { h.renderer.pageCount }
    }

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize =
        withContext(Dispatchers.IO) {
            val h = handle as Handle
            h.mutex.withLock {
                h.renderer.openPage(index).use { p ->
                    PageSize(p.width.toFloat(), p.height.toFloat())
                }
            }
        }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.IO) {
        val h = handle as Handle
        h.mutex.withLock {
            h.renderer.openPage(index).use { page ->
                val ratio = page.height.toFloat() / page.width.toFloat()
                val w = targetWidthPx.coerceAtLeast(1)
                val rawH = (w * ratio).toInt().coerceAtLeast(1)
                val maxDim = MAX_DIMENSION
                val (finalW, finalH) = if (rawH > maxDim) {
                    val scale = maxDim.toFloat() / rawH
                    ((w * scale).toInt().coerceAtLeast(1)) to maxDim
                } else w to rawH

                val bitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPage(
                    index = index,
                    widthPx = finalW,
                    heightPx = finalH,
                    bitmap = bitmap
                )
            }
        }
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? =
        withContext(Dispatchers.IO) {
            val h = handle as Handle
            val doc = h.pdDocument ?: return@withContext null
            try {
                val stripper = PDFTextStripper().apply {
                    // PDFTextStripper는 1-based 페이지 번호 사용
                    startPage = index + 1
                    endPage = index + 1
                }
                stripper.getText(doc).trim().ifBlank { "(이 페이지에 추출 가능한 텍스트가 없습니다 — 스캔 PDF일 수 있음)" }
            } catch (t: Throwable) {
                Timber.w(t, "PDF page $index 텍스트 추출 실패")
                null
            }
        }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching { h.renderer.close() }
                runCatching { h.pfd.close() }
                runCatching { h.pdDocument?.close() }
            }
        }
    }

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}
