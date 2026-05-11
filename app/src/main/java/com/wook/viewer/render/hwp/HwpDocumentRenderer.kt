package com.wook.viewer.render.hwp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageElement
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
        val images: List<Bitmap>,
        /** HWP 5.0 만 단락 단위 positioned 요소 보유. HWPX는 null → 플랫 폴백. */
        val pagedElements: List<List<PageElement>>?
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

        // HWP 5.0: hwplib 단락 워킹으로 인라인 위치 보존된 elements 생성
        val pagedElements = if (variant == HwpVariant.HWP_5) {
            runCatching { extractParagraphElements(temp, pages) }
                .onFailure { Timber.w(it, "HWP 단락 워킹 실패 — 플랫 폴백") }
                .getOrNull()
        } else null

        Timber.d("HWP 열기: variant=$variant, pages=${pages.size}, chars=${text.length}, images=${images.size}, paged=${pagedElements != null}")
        Handle(uri, temp, pages, variant, images, pagedElements)
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

    /** HWP는 단락 단위 positioned 요소가 있으면 거기서 이미지가 인라인 표시되므로
     *  플랫 이미지는 빈 리스트 반환. HWPX는 첫 페이지에 몰아 표시. */
    override suspend fun getPageImages(handle: DocumentHandle, index: Int): List<Bitmap> {
        val h = handle as Handle
        if (h.pagedElements != null) return emptyList()
        return if (index == 0) h.images else emptyList()
    }

    override suspend fun getPageElements(
        handle: DocumentHandle,
        index: Int
    ): List<PageElement>? {
        val h = handle as Handle
        return h.pagedElements?.getOrNull(index)
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
     *  - HWPX   : ZIP 안 BinData 폴더의 image 파일들
     */
    private fun extractImages(file: File, variant: HwpVariant): List<Bitmap> = when (variant) {
        HwpVariant.HWP_5 -> extractHwp5Images(file)
        HwpVariant.HWPX -> extractHwpxImages(file)
    }

    /**
     * HWP 5.0 단락 워킹 — 단락별 텍스트 + 그 단락의 ControlPicture 들을 PageElement 로.
     *
     * 전략 (정확한 위치까지는 못 가더라도 단락 단위로는 정확):
     *   1. TextExtractor 로 전체 평탄 텍스트 추출 → 페이지 분할
     *   2. doc.bodyText 의 paragraph 들을 순서대로 워킹
     *   3. 각 단락의 controlList 에서 picture 컨트롤 발견 시 그 단락의 binData 이미지 emit
     *   4. 페이지(텍스트 길이 기준 분할)와 단락 매칭: 단락 텍스트 누적 길이로 어느 페이지에
     *      속하는지 결정
     *
     * hwplib API가 변형되면 runCatching으로 보호.
     */
    private fun extractParagraphElements(
        file: File,
        textPages: List<TextPage>
    ): List<List<PageElement>>? = runCatching {
        val doc = kr.dogfoot.hwplib.reader.HWPReader.fromFile(file.absolutePath)
        val binItems = doc.binData?.embeddedBinaryDataList ?: emptyList<Any?>()

        // 단락 단위로 (text, images) 추출
        data class ParaPiece(val text: String, val images: List<Bitmap>)
        val pieces = mutableListOf<ParaPiece>()

        val sections = doc.bodyText?.sectionList ?: return@runCatching null
        for (section in sections) {
            val paragraphs = section.paragraphList ?: continue
            for (paragraph in paragraphs) {
                // 단락 텍스트 — TextExtractor 사용
                val paraText = runCatching {
                    kr.dogfoot.hwplib.tool.textextractor.TextExtractor.extract(
                        paragraph,
                        kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod
                            .InsertControlTextBetweenParagraphText
                    )
                }.getOrDefault("")

                // 단락의 ControlPicture 들
                val controls = paragraph.controlList ?: emptyList<Any?>()
                val paraImages = controls.mapNotNull { control ->
                    // ControlPicture 인지 type ID로 확인 — hwplib 내부 정의 의존성 회피
                    val ctrlClass = control?.javaClass ?: return@mapNotNull null
                    val isPicture = ctrlClass.simpleName?.let {
                        it == "ControlPicture" || it.contains("Picture")
                    } ?: false
                    if (!isPicture) return@mapNotNull null

                    // binItemID 메서드/필드 읽기
                    val binIdx = runCatching {
                        val m = ctrlClass.methods.firstOrNull {
                            it.name == "getBinItemID" || it.name.endsWith("getBinDataID")
                        }
                        (m?.invoke(control) as? Number)?.toInt()
                    }.getOrNull() ?: return@mapNotNull null

                    val binItem = binItems.getOrNull(binIdx - 1) ?: return@mapNotNull null
                    val bytes = runCatching {
                        val m = binItem.javaClass.methods.firstOrNull {
                            it.name == "getData" || it.name == "getBinaryData"
                        }
                        m?.invoke(binItem) as? ByteArray
                    }.getOrNull() ?: return@mapNotNull null

                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                }

                pieces += ParaPiece(paraText, paraImages)
            }
        }

        // 페이지 단위 슬롯에 단락 분배 — 페이지 텍스트 길이 누적 기준
        if (textPages.isEmpty()) return@runCatching null
        val pageElements = MutableList(textPages.size) { mutableListOf<PageElement>() }
        var pageIdx = 0
        var pageCharBudget = textPages[0].text.length
        for (piece in pieces) {
            if (pageIdx >= pageElements.size) break
            pageElements[pageIdx] += PageElement.TextElement(piece.text)
            piece.images.forEach { pageElements[pageIdx] += PageElement.ImageElement(it) }
            pageCharBudget -= piece.text.length
            if (pageCharBudget <= 0 && pageIdx < pageElements.size - 1) {
                pageIdx++
                pageCharBudget = textPages[pageIdx].text.length
            }
        }
        pageElements.map { it.toList() }
    }.onFailure { Timber.w(it, "HWP 단락 분석 중 예외 — 폴백") }.getOrNull()

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
