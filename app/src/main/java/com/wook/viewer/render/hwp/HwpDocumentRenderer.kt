package com.wook.viewer.render.hwp

import android.content.Context
import android.net.Uri
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.PageSize
import com.wook.viewer.domain.model.RenderedPage
import com.wook.viewer.domain.repository.DocumentHandle
import com.wook.viewer.domain.repository.DocumentRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HWP / HWPX 렌더러 (PoC).
 *
 * 처리 흐름:
 *   1. SAF URI → 캐시 디렉토리에 임시 파일 복사 (hwplib는 파일 경로 기반 API)
 *   2. 확장자/매직바이트로 HWP 5.0 vs HWPX 판별
 *   3. hwplib(HWP) 또는 hwpxlib(HWPX)로 텍스트 추출
 *   4. HwpPaginator로 논리 페이지 분할
 *   5. renderPage 호출 시 HwpTextRenderer로 Bitmap 생성
 *
 * PoC 한계 (의도적):
 *   - 표/이미지/도형 → 무시 (텍스트만)
 *   - 폰트/크기/색상 등 서식 → 일괄 기본값으로 단순화
 *   - 페이지 번호는 원본 한글 페이지와 일치하지 않음 (논리 분할)
 *   - 암호화/손상 파일 → 예외 발생 (UI에서 메시지 노출)
 */
@Singleton
class HwpDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormat: DocumentFormat = DocumentFormat.HWP

    private class Handle(
        override val uri: Uri,
        val tempFile: File,
        val pages: List<HwpPage>,
        val variant: HwpVariant
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
        val pages = HwpPaginator.paginate(text)
        Timber.d("HWP 열기 완료: variant=$variant, pages=${pages.size}, chars=${text.length}")
        Handle(uri, temp, pages, variant)
    }

    /**
     * hwplib/hwpxlib가 던지는 예외를 도메인 에러로 매핑.
     * 라이브러리가 모든 케이스에 대해 분류된 예외를 주지 않으므로 message 패턴 매칭을 일부 사용.
     */
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
        // A4 (포인트 단위)
        return PageSize(595f, 842f)
    }

    override suspend fun renderPage(
        handle: DocumentHandle,
        index: Int,
        targetWidthPx: Int
    ): RenderedPage = withContext(Dispatchers.Default) {
        val h = handle as Handle
        val page = h.pages.getOrElse(index) {
            HwpPage("(잘못된 페이지 인덱스: $index)")
        }
        HwpTextRenderer.render(page, targetWidthPx, index)
    }

    override suspend fun close(handle: DocumentHandle) = withContext(Dispatchers.IO) {
        val h = handle as Handle
        runCatching { h.tempFile.delete() }
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
        // 매직바이트 기반 폴백
        return if (isZipMagic(file)) HwpVariant.HWPX else HwpVariant.HWP_5
    }

    private fun isZipMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val b0 = input.read(); val b1 = input.read()
            b0 == 0x50 && b1 == 0x4B  // 'P' 'K'
        }
    }.getOrDefault(false)

    /**
     * 텍스트 추출.
     *
     * hwplib/hwpxlib는 Java 라이브러리이며 클래스 패키지에 Kotlin 키워드 `object`가 포함되어
     * 직접 import는 어색하다. 본 함수는 리플렉션 없는 정상 경로를 사용하되,
     * 외부 타입을 변수로 보유하지 않고 한 줄 체인으로만 호출한다.
     */
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
                kr.dogfoot.hwpxlib.tool.textextractor.TextMarks()
            )
        }
    }
}

