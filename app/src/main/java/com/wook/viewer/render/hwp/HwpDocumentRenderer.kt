package com.wook.viewer.render.hwp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HWP / HWPX 렌더러.
 *
 * v0.6.3 추가: BinData(임베디드 이미지) 추출
 *   - HWP 5.0 → hwplib BinData API
 *   - HWPX    → ZIP 안 BinData/ 폴더 직접 워크
 */
@Singleton
class HwpDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(DocumentFormat.HWP)

    private class Handle(
        override val uri: Uri,
        val tempFile: File,
        val pages: List<TextPage>,
        val variant: HwpVariant,
        val images: List<Bitmap>
    ) : DocumentHandle

    private enum class HwpVariant { HWP_5, HWPX }

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val temp = try {
            copyToCache(uri)
        } catch (t: Throwable) {
            Timber.e(t, "HWP 캐시 복사 실패")
            throw DocumentError.IoError(t)
        }

        val variant = detectVariant(uri, temp)
        val text = try {
            extractText(temp, variant)
        } catch (t: Throwable) {
            Timber.e(t, "HWP 텍스트 추출 실패")
            temp.delete()
            throw classifyHwpError(t)
        }
        val pages = TextPaginator.paginate(text)
        val images = runCatching { extractImages(temp, variant) }
            .onFailure { Timber.w(it, "HWP 이미지 추출 실패 — 텍스트만 표시") }
            .getOrDefault(emptyList())

        Timber.d("HWP 열기 완료: variant=$variant, pages=${pages.size}, chars=${text.length}, images=${images.size}")
        Handle(uri, temp, pages, variant, images)
    }

    private fun classifyHwpError(t: Throwable): DocumentError {
        val msg = (t.message ?: "").lowercase()
        return when {
            "password" in msg || "encrypt" in msg || "암호" in msg ->
                DocumentError.PasswordProtected(t)
            "unsupported" in msg || "version" in msg ->
                DocumentError.UnsupportedVariant("hwp/hwpx", t)
            "io" in msg || "stream" in msg || "eof" in msg ->
                DocumentError.IoError(t)
            t is java.io.IOException ->
                DocumentError.IoError(t)
            else ->
                DocumentError.Corrupted(t)
        }
    }

    override suspend fun pageCount(handle: DocumentHandle): Int =
        (handle as Handle).pages.size

    override suspend fun pageSize(handle: DocumentHandle, index: Int): PageSize {
        return PageSize(595f, 842f)
    }

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

    /** HWP는 위치 정보가 복잡해서 모든 이미지를 첫 페이지에 표시. */
    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        return if (index == 0) h.images else emptyList()
    }

    override suspend fun close(handle: DocumentHandle) {
        val h = handle as Handle
        withContext(Dispatchers.IO) {
            runCatching { h.tempFile.delete() }
            h.images.forEach { runCatching { it.recycle() } }
        }
    }

    // ---- 내부 ----

    private fun copyToCache(uri: Uri): File {
        val temp = File.createTempFile("hwp_", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "openInputStream returned null for $uri" }
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        return temp
    }

    private fun detectVariant(uri: Uri, file: File): HwpVariant {
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".hwpx", ignoreCase = true)) return HwpVariant.HWPX
        if (name.endsWith(".hwp", ignoreCase = true)) return HwpVariant.HWP_5
        return if (isZipMagic(file)) HwpVariant.HWPX else HwpVariant.HWP_5
    }

    private fun isZipMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val b0 = input.read(); val b1 = input.read()
            b0 == 0x50 && b1 == 0x4B
        }
    }.getOrDefault(false)

    private fun extractText(file: File, variant: HwpVariant): String = when (variant) {
        HwpVariant.HWP_5 -> {
            val doc = kr.dogfoot.hwplib.reader.HWPReader.fromFile(file.absolutePath)
            kr.dogfoot.hwplib.tool.textextractor.TextExtractor.extract(
                doc,
                kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText
            )
        }
        HwpVariant.HWPX -> {
            val doc = kr.dogfoot.hwpxlib.reader.HWPXReader.fromFilepath(file.absolutePath)
            kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor.extract(
                doc,
                kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod.InsertControlTextBetweenParagraphText,
                /* insertParaHead = */ true,
                kr.dogfoot.hwpxlib.tool.textextractor.TextMarks()
            )
        }
    }

    /**
     * 이미지 추출.
     *  - HWP 5.0: hwplib BinData → EmbeddedBinaryData 리스트
     *  - HWPX   : ZIP 안 BinData/ 폴더의 image 파일들
     */
    private fun extractImages(file: File, variant: HwpVariant): List<Bitmap> = when (variant) {
        HwpVariant.HWP_5 -> extractHwp5Images(file)
        HwpVariant.HWPX -> extractHwpxImages(file)
    }

    private fun extractHwp5Images(file: File): List<Bitmap> = runCatching {
        val doc = kr.dogfoot.hwplib.reader.HWPReader.fromFile(file.absolutePath)
        val binData = doc.binData ?: return@runCatching emptyList<Bitmap>()
        val list = binData.embeddedBinaryDataList ?: return@runCatching emptyList<Bitmap>()
        list.mapNotNull { item ->
            val bytes = item.data ?: return@mapNotNull null
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun extractHwpxImages(file: File): List<Bitmap> {
        val images = mutableListOf<Bitmap>()
        val imageExt = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
        runCatching {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.startsWith("bindata/") &&
                        name.substringAfterLast('.', "") in imageExt
                    ) {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8 * 1024)
                        var n = zis.read(buf)
                        while (n > 0) {
                            out.write(buf, 0, n)
                            n = zis.read(buf)
                        }
                        val bytes = out.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) images.add(bmp)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return images
    }
}
