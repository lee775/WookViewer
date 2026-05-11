package com.wook.viewer.render.lok

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.wook.viewer.data.lok.LokSession
import com.wook.viewer.domain.error.DocumentError
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
import org.libreoffice.kit.Document
import org.libreoffice.kit.DirectBufferAllocator
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 기반 렌더러 — Office 파일 100% 재현 목표.
 *
 * **현재 상태 (Session 1 + S3 사전 준비)**:
 *   - S2 (native lib 빌드) 완료 시 자동 활성화
 *   - JNI API 호출 코드는 준비되어 있음 — 라이브러리가 들어오면 동작
 *
 * 흐름:
 *   1. SAF URI → cacheDir 로 복사 (LOK 은 file:// 만 받음)
 *   2. office.documentLoad("file://...") → Document 핸들
 *   3. pageCount = doc.getParts() (presentation/spreadsheet) 또는 페이지네이션
 *   4. renderPage = paintTileNative 로 ARGB DirectBuffer 채워서 Bitmap 만들기
 *
 * 좌표 단위:
 *   - LOK 은 twips (1 inch = 1440 twips)
 *   - 우리 viewer는 px (target width 기반)
 *   - 변환: twips * (1/1440 inch) * (DPI px/inch)
 *   - 단순화: ratio = targetPx / docWidthTwips
 */
@Singleton
class LokDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: LokSession
) : DocumentRenderer {

    /**
     * 일부러 빈 set: Hilt @IntoSet 으로 등록되어도
     * 기존 [DocumentFormat] → renderer 매핑을 덮어쓰지 않도록.
     * 실제 라우팅은 RendererRegistryImpl 이 [LOK_SUPPORTED_FORMATS] 를 참조해 별도 처리.
     */
    override val supportedFormats: Set<DocumentFormat> = emptySet()

    private class Handle(
        override val uri: Uri,
        val document: Document,
        val tempFile: File,
        val mutex: Mutex = Mutex()
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val office = session.getOffice()
            ?: throw DocumentError.Unknown(IllegalStateException("LokSession 미초기화"))

        // LOK은 file:// 만 받음 — content:// 라면 cacheDir 로 복사
        val tempFile = copyToCache(uri)
        val fileUrl = "file://${tempFile.absolutePath}"

        val document = office.documentLoad(fileUrl)
            ?: run {
                val err = runCatching { office.error }.getOrNull() ?: "(unknown)"
                tempFile.delete()
                // password 보호 문서 감지: LOK은 documentLoad에서 null 반환 + error 메시지
                if (err.contains("password", ignoreCase = true) ||
                    err.contains("encrypt", ignoreCase = true)
                ) {
                    throw DocumentError.PasswordProtected()
                }
                throw DocumentError.Corrupted(RuntimeException("LOK documentLoad 실패: $err"))
            }

        runCatching { document.initializeForRendering() }
            .onFailure { Timber.w(it, "initializeForRendering 실패 — 일부 렌더 결함 가능") }

        Handle(uri, document, tempFile)
    }

    override suspend fun pageCount(handle: DocumentHandle): Int {
        val h = handle as Handle
        return h.mutex.withLock { h.document.parts }
    }

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize {
        val h = handle as Handle
        return h.mutex.withLock {
            if (h.document.parts > 1) h.document.setPart(index)
            // twips → points (1pt = 20 twips)
            PageSize(
                widthPt = h.document.documentWidth.toFloat() / 20f,
                heightPt = h.document.documentHeight.toFloat() / 20f
            )
        }
    }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.IO) {
        val h = handle as Handle
        h.mutex.withLock {
            if (h.document.parts > 1) h.document.setPart(index)

            val docWidthTwips = h.document.documentWidth.toInt()
            val docHeightTwips = h.document.documentHeight.toInt()
            if (docWidthTwips <= 0 || docHeightTwips <= 0) {
                throw DocumentError.Corrupted(
                    IllegalStateException("LOK 문서 크기 0: w=$docWidthTwips h=$docHeightTwips")
                )
            }

            val widthPx = targetWidthPx.coerceAtLeast(1)
            val heightPx = (widthPx.toLong() * docHeightTwips / docWidthTwips)
                .toInt().coerceAtLeast(1)

            val bufferSize = widthPx * heightPx * 4  // ARGB 8888
            val buffer: ByteBuffer = DirectBufferAllocator.allocate(bufferSize)

            // LOK paintTile: ARGB DirectBuffer 에 그림. 전체 페이지를 한 타일로 요청.
            h.document.paintTile(
                buffer,
                /* canvasWidth */ widthPx,
                /* canvasHeight */ heightPx,
                /* tilePositionX */ 0,
                /* tilePositionY */ 0,
                /* tileWidth */ docWidthTwips,
                /* tileHeight */ docHeightTwips
            )

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            DirectBufferAllocator.free(buffer)

            RenderedPage(
                index = index,
                widthPx = widthPx,
                heightPx = heightPx,
                bitmap = bitmap
            )
        }
    }

    override suspend fun open(uri: Uri, password: String): DocumentHandle {
        val office = session.getOffice()
            ?: throw DocumentError.Unknown(IllegalStateException("LokSession 미초기화"))

        val tempFile = copyToCache(uri)
        val fileUrl = "file://${tempFile.absolutePath}"

        // LOK에 비밀번호 설정 후 로드 시도
        office.setDocumentPassword(fileUrl, password)
        val document = office.documentLoad(fileUrl)
            ?: run {
                val err = runCatching { office.error }.getOrNull() ?: ""
                tempFile.delete()
                val wrong = err.contains("password", ignoreCase = true)
                throw DocumentError.PasswordProtected(wrongPassword = wrong)
            }
        runCatching { document.initializeForRendering() }
        return Handle(uri, document, tempFile)
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching { h.document.destroy() }
                runCatching { h.tempFile.delete() }
            }
        }
    }

    private fun copyToCache(uri: Uri): File {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
            ?: "bin"
        val temp = File.createTempFile("lok_", ".$ext", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "openInputStream returned null for $uri" }
            temp.outputStream().use { input.copyTo(it) }
        }
        return temp
    }
}

/** LOK 라우팅 대상 포맷 — RendererRegistryImpl 이 활성화 조건과 함께 사용. */
val LOK_SUPPORTED_FORMATS: Set<DocumentFormat> = setOf(
    DocumentFormat.DOCX,
    DocumentFormat.PPTX,
    DocumentFormat.XLSX,
    DocumentFormat.HWP
)
