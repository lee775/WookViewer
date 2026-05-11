package com.wook.viewer.render.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.OutlineNode
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
import java.io.File
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
        /** 비밀번호 해제 후 만들어진 캐시 파일. close 시 삭제. null이면 일반 PDF. */
        val unlockedCache: File? = null,
        val mutex: Mutex = Mutex()
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        // 1차: 평문 PDF 로드 시도
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open file descriptor for $uri")
        } catch (t: Throwable) {
            throw t
        }

        val renderer = try {
            PdfRenderer(pfd)
        } catch (se: SecurityException) {
            // 암호 PDF — PdfRenderer는 SecurityException 던짐
            runCatching { pfd.close() }
            Timber.i("PDF 암호 보호 감지 (PdfRenderer SecurityException)")
            throw DocumentError.PasswordProtected(wrongPassword = false, cause = se)
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            // PdfBox로 다시 시도해 암호 보호 여부 식별
            val isEncrypted = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { it.isEncrypted }
                } ?: false
            }.getOrDefault(false)
            if (isEncrypted) {
                throw DocumentError.PasswordProtected(wrongPassword = false, cause = t)
            }
            throw t
        }

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

    /**
     * 비밀번호로 PDF를 연다. PdfBox로 복호화 → 평문 PDF를 cacheDir에 저장 → PdfRenderer로 오픈.
     *
     * 캐시 파일은 [close] 시 삭제된다.
     */
    override suspend fun open(uri: Uri, password: String): DocumentHandle = withContext(Dispatchers.IO) {
        // PdfBox로 복호화 시도
        val decryptedDoc = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password)
            } ?: throw DocumentError.IoError()
        } catch (ipe: InvalidPasswordException) {
            Timber.i("PDF 비밀번호 불일치")
            throw DocumentError.PasswordProtected(wrongPassword = true, cause = ipe)
        } catch (de: DocumentError) {
            throw de
        } catch (t: Throwable) {
            Timber.w(t, "PDF 복호화 실패")
            throw DocumentError.Corrupted(t)
        }

        // 보안 정보 제거 후 캐시에 저장
        val cacheFile = createUnlockedCacheFile()
        try {
            decryptedDoc.use { doc ->
                doc.setAllSecurityToBeRemoved(true)
                doc.save(cacheFile)
            }
        } catch (t: Throwable) {
            runCatching { cacheFile.delete() }
            Timber.w(t, "복호화된 PDF 저장 실패")
            throw DocumentError.Corrupted(t)
        }

        // 캐시 파일을 PdfRenderer로 오픈
        val pfd = try {
            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            runCatching { cacheFile.delete() }
            throw DocumentError.IoError(t)
        }
        val renderer = try {
            PdfRenderer(pfd)
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            runCatching { cacheFile.delete() }
            throw DocumentError.Corrupted(t)
        }

        // 텍스트 추출용 PDDocument도 캐시 파일에서 로드 (이미 평문)
        val pdDocument = runCatching {
            cacheFile.inputStream().use { PDDocument.load(it) }
        }.onFailure {
            Timber.w(it, "캐시 PDF 텍스트 추출 비활성")
        }.getOrNull()

        Handle(uri, pfd, renderer, pdDocument, unlockedCache = cacheFile)
    }

    private fun createUnlockedCacheFile(): File {
        val dir = File(context.cacheDir, UNLOCK_CACHE_DIR).apply { mkdirs() }
        // 같은 디렉터리의 오래된 잔여 캐시 정리 (이전 세션 비정상 종료 대비)
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
        return File.createTempFile("unlocked-", ".pdf", dir)
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

    override suspend fun getOutline(handle: DocumentHandle): List<OutlineNode>? =
        withContext(Dispatchers.IO) {
            val h = handle as Handle
            val doc = h.pdDocument ?: return@withContext null
            val outline = runCatching {
                doc.documentCatalog?.documentOutline
            }.getOrNull() ?: return@withContext null

            val roots = mutableListOf<OutlineNode>()
            var child: PDOutlineItem? = runCatching { outline.firstChild }.getOrNull()
            while (child != null) {
                runCatching { toOutlineNode(child!!, doc, depth = 0) }
                    .getOrNull()
                    ?.let { roots += it }
                child = runCatching { child!!.nextSibling }.getOrNull()
            }
            roots.takeIf { it.isNotEmpty() }
        }

    /** PDOutlineItem 트리를 재귀 변환. 깊이 제한으로 순환 방어. */
    private fun toOutlineNode(item: PDOutlineItem, doc: PDDocument, depth: Int): OutlineNode {
        val title = (item.title ?: "").trim()
        val pageIndex = resolvePageIndex(item, doc)
        val children = mutableListOf<OutlineNode>()
        if (depth < MAX_OUTLINE_DEPTH) {
            var child: PDOutlineItem? = runCatching { item.firstChild }.getOrNull()
            while (child != null) {
                runCatching { toOutlineNode(child!!, doc, depth + 1) }
                    .getOrNull()
                    ?.let { children += it }
                child = runCatching { child!!.nextSibling }.getOrNull()
            }
        }
        return OutlineNode(title = title, pageIndex = pageIndex, children = children)
    }

    /** 목차 항목의 목적지를 0-기반 페이지 번호로 변환. 실패 시 null. */
    private fun resolvePageIndex(item: PDOutlineItem, doc: PDDocument): Int? {
        // 1. 직접 destination
        runCatching {
            when (val dest = item.destination) {
                is PDPageDestination -> {
                    val n = dest.retrievePageNumber()
                    if (n >= 0) return n
                }
                is PDNamedDestination -> {
                    val resolved = doc.documentCatalog?.findNamedDestinationPage(dest)
                    val n = resolved?.retrievePageNumber()
                    if (n != null && n >= 0) return n
                }
                else -> Unit
            }
        }
        // 2. Action 경유 (PDActionGoTo)
        runCatching {
            val action = item.action
            if (action is PDActionGoTo) {
                when (val ad = action.destination) {
                    is PDPageDestination -> {
                        val n = ad.retrievePageNumber()
                        if (n >= 0) return n
                    }
                    is PDNamedDestination -> {
                        val resolved = doc.documentCatalog?.findNamedDestinationPage(ad)
                        val n = resolved?.retrievePageNumber()
                        if (n != null && n >= 0) return n
                    }
                    else -> Unit
                }
            }
        }
        return null
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            h.mutex.withLock {
                runCatching { h.renderer.close() }
                runCatching { h.pfd.close() }
                runCatching { h.pdDocument?.close() }
                h.unlockedCache?.let { runCatching { it.delete() } }
            }
        }
    }

    private companion object {
        const val MAX_DIMENSION = 4096
        const val UNLOCK_CACHE_DIR = "pdf-unlocked"
        const val MAX_OUTLINE_DEPTH = 20
    }
}
