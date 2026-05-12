package com.wook.viewer.render.text

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
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Markdown(.md) + 일반 텍스트(.txt/.csv/.json/.xml/.yaml/...) 통합 렌더러.
 *
 * 인코딩:
 *  1. UTF-8/16 BOM 검사
 *  2. UTF-8 strict
 *  3. EUC-KR/CP949 (한국어 Windows 텍스트 파일)
 *  4. ISO-8859-1 (lossy fallback — 항상 성공)
 *
 * Markdown 포맷팅(헤더, 굵게 등)은 v0.4.8에서 미적용 — 평문 그대로 표시 + 선택/복사.
 * 향후 v0.5+에서 AnnotatedString 기반 렌더링 도입 검토.
 */
@Singleton
class PlainTextDocumentRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentRenderer {

    override val supportedFormats: Set<DocumentFormat> = setOf(
        DocumentFormat.MARKDOWN,
        DocumentFormat.PLAIN_TEXT
    )

    private class Handle(
        override val uri: Uri,
        val pages: List<TextPage>,
        val detectedCharset: String
    ) : DocumentHandle

    override suspend fun open(uri: Uri): DocumentHandle = withContext(Dispatchers.IO) {
        val (text, charset) = try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null" }
                readBestEffort(input)
            }
        } catch (t: Throwable) {
            Timber.e(t, "텍스트 파일 읽기 실패")
            throw if (t is java.io.IOException) DocumentError.IoError(t)
            else DocumentError.Corrupted(t)
        }

        val pages = TextPaginator.paginate(text)
        Timber.d("text 열기 완료: charset=$charset, pages=${pages.size}, chars=${text.length}")
        Handle(uri, pages, charset)
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
        // 리소스 없음 — 스트리밍 추출 후 메모리만 보유
    }

    // ---- 인코딩 폴백 ----

    private fun readBestEffort(input: InputStream): Pair<String, String> {
        val bytes = input.readBytes()
        Timber.d("text: ${bytes.size} bytes read")

        // BOM 검사
        when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8 (BOM)"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16 LE (BOM)"
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16 BE (BOM)"
        }

        // UTF-8 strict 시도
        decodeStrict(bytes, StandardCharsets.UTF_8)?.let {
            Timber.d("text decoded as UTF-8")
            return it to "UTF-8"
        }

        // 한국어 인코딩 폴백 — Android 가용 charset 더 넓게.
        // CP949 가 EUC-KR superset (한글 완성형 + 확장)이라 보통 CP949 먼저 시도 권장.
        val koreanCharsets = listOf(
            "x-windows-949",   // Android 표준 alias
            "MS949",
            "CP949",
            "IBM949",
            "EUC-KR",
            "KS_C_5601-1987",  // EUC-KR alias
            "x-EUC-KR"
        )
        for (name in koreanCharsets) {
            val cs = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            decodeStrict(bytes, cs)?.let {
                Timber.i("text decoded as $name (alias: ${cs.name()})")
                return it to cs.name()
            }
        }
        Timber.w("text: no strict Korean charset matched — trying lenient")

        // 가용 charset 들 strict 모두 실패 → CP949/EUC-KR 으로 replace 모드 시도
        // (일부 글자가 ? 로 치환돼도 한글 본문은 살려둠)
        for (name in koreanCharsets) {
            val cs = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            runCatching {
                val decoder = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                val s = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                Timber.i("text decoded as $name (lenient)")
                return s to "$name (lenient)"
            }
        }

        // 마지막 수단
        Timber.w("text: falling back to ISO-8859-1 (한글 깨짐 가능)")
        return String(bytes, Charsets.ISO_8859_1) to "ISO-8859-1 (fallback)"
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrElse {
        if (it is CharacterCodingException) null else throw it
    }
}
