package com.wook.viewer.render.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 내장 PdfRenderer 기반 PDF 렌더러.
 *
 * 한계:
 *  - 암호화 PDF 미지원 (필요 시 PdfiumAndroid로 교체)
 *  - 텍스트 추출/검색 미지원 (별도 라이브러리 필요)
 *
 * 동시성:
 *  - PdfRenderer는 한 번에 한 페이지만 열 수 있어 mutex로 직렬화한다.
 */
@Singleton
class PdfDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormat: DocumentFormat = DocumentFormat.PDF

    private class Handle(
        override val uri: Uri,
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer,
        val mutex: Mutex = Mutex()
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open file descriptor for $uri")
        val renderer = PdfRenderer(pfd)
        Handle(uri, pfd, renderer)
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
                // 메모리 안전을 위해 한쪽 변을 제한
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

    override suspend fun close(handle: DocumentHandle) = withContext(Dispatchers.IO) {
        val h = handle as Handle
        h.mutex.withLock {
            runCatching { h.renderer.close() }
            runCatching { h.pfd.close() }
        }
    }

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}
