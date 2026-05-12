package com.wook.viewer.render.lok

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
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
import org.libreoffice.kit.DirectBufferAllocator
import org.libreoffice.kit.Document
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LibreOfficeKit 기반 렌더러 — Office 파일 100% 재현 목표 (S3-S4 단계).
 *
 * 단위 시스템:
 *   - LOK 은 twips (1 inch = 1440 twips)
 *   - 우리 viewer는 px (target width 기반)
 *   - 변환: twips * (1/1440 inch) * (target_dpi px/inch)
 *
 * 페이지/파트 모델:
 *   - PPTX/XLSX: 각 part = 1 슬라이드/시트. setPart() 로 전환
 *   - DOCX: 일반적으로 1 part 이고 longa-page. parts > 1 이면 그대로 사용.
 *     (Calc 가 아닌 Writer 는 LOK 가 자동 페이지화 안 함)
 *
 * 견고화 (S3):
 *   - 모든 JNI 호출 runCatching 으로 보호 — 실패 시 DocumentError 로 변환
 *   - Mutex 로 동시 호출 직렬화 (LOK Document 가 thread-safe 하지 않음)
 *   - 비트맵 LRU 캐시 (3-5 페이지) — paintTile 재호출 회피
 *   - 캐시 dispose 시 비트맵 recycle
 */
@Singleton
class LokDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: LokSession
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = emptySet()

    private class Handle(
        override val uri: Uri,
        val document: Document,
        val tempFile: File,
        val docType: Int,
        val partCount: Int,
        val mutex: Mutex = Mutex(),
        val bitmapCache: BitmapLruCache = BitmapLruCache(BITMAP_CACHE_SIZE_BYTES)
    ) : DocumentHandle

    private data class CacheKey(val partIndex: Int, val widthPx: Int)

    /**
     * 페이지 비트맵 LRU 캐시.
     * 같은 page+width 조합은 재사용, evict 된 비트맵은 즉시 recycle 해서 메모리 회수.
     */
    private class BitmapLruCache(maxSize: Int) : LruCache<CacheKey, Bitmap>(maxSize) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount
        override fun entryRemoved(
            evicted: Boolean,
            key: CacheKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) runCatching { oldValue.recycle() }
        }
    }

    override suspend fun open(uri: Uri): DocumentHandle = openInternal(uri, password = null)

    override suspend fun open(uri: Uri, password: String): DocumentHandle =
        openInternal(uri, password)

    private suspend fun openInternal(uri: Uri, password: String?): DocumentHandle =
        withContext(Dispatchers.IO) {
            val office = session.getOffice()
                ?: throw DocumentError.Unknown(IllegalStateException("LokSession 미초기화"))

            val tempFile = copyToCache(uri)
            val fileUrl = "file://${tempFile.absolutePath}"

            // 비밀번호가 있으면 먼저 설정 (LOK 은 다음 load 호출에서 사용)
            if (!password.isNullOrEmpty()) {
                runCatching { office.setDocumentPassword(fileUrl, password) }
                    .onFailure { Timber.w(it, "setDocumentPassword 실패") }
            }

            val document = runCatching { office.documentLoad(fileUrl) }
                .getOrElse {
                    tempFile.delete()
                    Timber.e(it, "documentLoad 예외")
                    throw DocumentError.Corrupted(it)
                } ?: run {
                    val err = runCatching { office.error }.getOrNull().orEmpty()
                    tempFile.delete()
                    throw classifyOpenError(err, password != null)
                }

            runCatching { document.initializeForRendering() }
                .onFailure { Timber.w(it, "initializeForRendering 실패 — 일부 렌더 결함 가능") }

            val docType = runCatching { document.documentType }.getOrDefault(Document.DOCTYPE_OTHER)
            val partCount = runCatching { document.parts.coerceAtLeast(1) }.getOrDefault(1)
            Timber.i("LOK 문서 열기: type=$docType, parts=$partCount, file=${tempFile.name}")

            Handle(uri, document, tempFile, docType, partCount)
        }

    override suspend fun pageCount(handle: DocumentHandle): Int {
        val h = handle as Handle
        return h.partCount  // open 시 한 번 측정해 두고 재사용
    }

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize {
        val h = handle as Handle
        return h.mutex.withLock {
            runCatching {
                if (h.partCount > 1) h.document.setPart(index.coerceIn(0, h.partCount - 1))
                PageSize(
                    widthPt = h.document.documentWidth.toFloat() / TWIPS_PER_POINT,
                    heightPt = h.document.documentHeight.toFloat() / TWIPS_PER_POINT
                )
            }.getOrElse {
                Timber.w(it, "pageSize 실패 — 기본값 반환")
                PageSize(595f, 842f)  // A4 기본
            }
        }
    }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.IO) {
        val h = handle as Handle
        val widthPx = targetWidthPx.coerceAtLeast(MIN_WIDTH_PX)
        val cacheKey = CacheKey(index, widthPx)

        // 캐시 hit
        h.bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) {
                return@withContext RenderedPage(
                    index = index,
                    widthPx = cached.width,
                    heightPx = cached.height,
                    bitmap = cached
                )
            }
            h.bitmapCache.remove(cacheKey)  // 회수된 비트맵 청소
        }

        h.mutex.withLock {
            // 동시에 같은 페이지 요청 시 다른 쪽이 캐시 채웠을 수 있음
            h.bitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) {
                    return@withLock RenderedPage(index, cached.width, cached.height, cached)
                }
            }

            if (h.partCount > 1) {
                runCatching { h.document.setPart(index.coerceIn(0, h.partCount - 1)) }
                    .onFailure { Timber.w(it, "setPart($index) 실패") }
            }

            val docWidthTwips = runCatching { h.document.documentWidth }.getOrDefault(0L).toInt()
            val docHeightTwips = runCatching { h.document.documentHeight }.getOrDefault(0L).toInt()
            if (docWidthTwips <= 0 || docHeightTwips <= 0) {
                throw DocumentError.Corrupted(
                    IllegalStateException("LOK 문서 크기 0: w=$docWidthTwips h=$docHeightTwips")
                )
            }

            val heightPx = (widthPx.toLong() * docHeightTwips / docWidthTwips)
                .toInt().coerceAtLeast(1).coerceAtMost(MAX_HEIGHT_PX)

            // 비트맵을 위해 DirectBuffer 할당
            val bufferSize = widthPx * heightPx * 4  // ARGB 8888
            val buffer: ByteBuffer = try {
                DirectBufferAllocator.allocate(bufferSize)
            } catch (oom: OutOfMemoryError) {
                Timber.e(oom, "DirectBuffer 할당 실패 — $bufferSize bytes")
                throw DocumentError.Unknown(oom)
            }

            try {
                // LOK paintTile: ARGB DirectBuffer 에 그림. 전체 페이지를 한 타일로 요청.
                runCatching {
                    h.document.paintTile(
                        buffer,
                        widthPx,
                        heightPx,
                        0,
                        0,
                        docWidthTwips,
                        docHeightTwips
                    )
                }.getOrElse {
                    Timber.e(it, "paintTile($index) 실패")
                    throw DocumentError.Corrupted(it)
                }

                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)

                // 캐시 저장 — 평가 후 LRU 가 알아서 evict
                h.bitmapCache.put(cacheKey, bitmap)

                RenderedPage(
                    index = index,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    bitmap = bitmap
                )
            } finally {
                runCatching { DirectBufferAllocator.free(buffer) }
            }
        }
    }

    override suspend fun getPageText(handle: DocumentHandle, index: Int): String? {
        val h = handle as Handle
        return withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching {
                    if (h.partCount > 1) h.document.setPart(index.coerceIn(0, h.partCount - 1))
                    // 1. 전체 선택
                    h.document.postUnoCommand(".uno:SelectAll", null, false)
                    // 2. 선택 텍스트 추출 — text/plain;charset=utf-8
                    val text = h.document.getTextSelection("text/plain;charset=utf-8")
                    // 3. 선택 해제 (다음 페이지 위해)
                    h.document.resetSelection()
                    text?.takeIf { it.isNotBlank() }
                }.getOrElse {
                    Timber.w(it, "getPageText($index) 실패")
                    null
                }
            }
        }
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                // 캐시된 비트맵 해제
                h.bitmapCache.evictAll()
                runCatching { h.document.destroy() }
                    .onFailure { Timber.w(it, "Document.destroy 실패") }
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

    private fun classifyOpenError(errorMsg: String, hadPassword: Boolean): DocumentError {
        val lower = errorMsg.lowercase()
        return when {
            "password" in lower || "encrypted" in lower ->
                DocumentError.PasswordProtected(wrongPassword = hadPassword)
            "unsupported" in lower || "version" in lower ->
                DocumentError.UnsupportedVariant("LOK 미지원 변형", RuntimeException(errorMsg))
            else -> DocumentError.Corrupted(RuntimeException("LOK documentLoad 실패: $errorMsg"))
        }
    }

    private companion object {
        const val TWIPS_PER_POINT = 20f  // 1 pt = 20 twips
        const val MIN_WIDTH_PX = 100
        const val MAX_HEIGHT_PX = 8192  // 안전 한계
        // 비트맵 캐시 ~30MB (페이지 ARGB 1080x1512 ≈ 6.5MB → ~4-5 페이지)
        const val BITMAP_CACHE_SIZE_BYTES = 30 * 1024 * 1024
    }
}

/**
 * LOK 라우팅 대상 포맷 — RendererRegistryImpl 이 활성화 조건과 함께 사용.
 *
 * HWP 제외 이유: 우리 enum 은 .hwp 와 .hwpx 를 한 묶음으로 본다.
 *   - LO 는 HWP 5.0 만 (hwpfilter 모듈) 지원, HWPX 미지원.
 *   - .hwpx 가 LOK 로 라우팅되면 깨짐.
 *   - 안전하게 기존 hwplib/hwpxlib 경로 유지.
 */
val LOK_SUPPORTED_FORMATS: Set<DocumentFormat> = setOf(
    DocumentFormat.DOCX,
    DocumentFormat.PPTX,
    DocumentFormat.XLSX
)
