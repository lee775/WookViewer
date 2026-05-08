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

        // UTF-8 strict 시도 — 잘못된 바이트면 throw
        decodeStrict(bytes, StandardCharsets.UTF_8)?.let { return it to "UTF-8" }

        // 한국어 Windows 텍스트 파일 — EUC-KR / CP949 fallback
        val koreanCharsets = listOf("EUC-KR", "x-windows-949", "CP949", "MS949")
        for (name in koreanCharsets) {
            runCatching { Charset.forName(name) }.getOrNull()?.let { cs ->
                decodeStrict(bytes, cs)?.let { return it to cs.name() }
            }
        }

        // 최후 수단 — ISO-8859-1은 모든 바이트 시퀀스에 대해 성공 (단 한글은 깨짐)
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
