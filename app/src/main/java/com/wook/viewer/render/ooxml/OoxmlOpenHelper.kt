package com.wook.viewer.render.ooxml

import android.content.Context
import com.wook.viewer.data.crypt.OoxmlDecryptionException
import com.wook.viewer.data.crypt.OoxmlDecryptor
import com.wook.viewer.data.crypt.WrongPasswordException
import com.wook.viewer.domain.error.DocumentError
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * OOXML(DOCX/PPTX/XLSX) 공통 open 헬퍼.
 *
 * 흐름:
 *   1. 입력 스트림 헤더 검사 → ZIP(평문) / CFB(암호화) 판별
 *   2. ZIP → 그대로 [extract] 람다에 위임
 *   3. CFB + password 없음 → [DocumentError.PasswordProtected] 던짐
 *   4. CFB + password → POI로 복호화 → 캐시 파일 → 평문 스트림으로 [extract] 호출
 *
 * 캐시 파일은 [onCachedFile] 콜백으로 호출자에게 전달되어 핸들에 보관되었다가
 * close 시 삭제되도록 한다.
 */
internal object OoxmlOpenHelper {

    private const val UNLOCK_CACHE_DIR = "ooxml-unlocked"

    /**
     * @param T 호출자가 만드는 결과 객체 (예: extractor가 반환하는 content data)
     * @param input 원본 InputStream (이 함수가 닫음)
     * @param extract 평문 ZIP InputStream을 받아 결과를 만드는 람다
     * @param onCachedFile 복호화된 캐시 파일이 만들어진 경우 호출 (close 시 삭제용)
     */
    fun <T> openOrThrow(
        context: Context,
        input: InputStream,
        password: String?,
        extract: (InputStream) -> T,
        onCachedFile: (File) -> Unit = {}
    ): T {
        val (detection, stream) = OoxmlDecryptor.detect(input)
        return when (detection) {
            OoxmlDecryptor.Detection.ZIP_OOXML, OoxmlDecryptor.Detection.UNKNOWN -> {
                // UNKNOWN도 일단 extract 시도 — 기존 동작 보존
                stream.use { extract(it) }
            }
            OoxmlDecryptor.Detection.ENCRYPTED_OOXML -> {
                if (password.isNullOrEmpty()) {
                    runCatching { stream.close() }
                    throw DocumentError.PasswordProtected(wrongPassword = false)
                }
                val cache = createCacheFile(context)
                try {
                    stream.use { OoxmlDecryptor.decryptTo(it, password, cache) }
                } catch (e: WrongPasswordException) {
                    runCatching { cache.delete() }
                    throw DocumentError.PasswordProtected(wrongPassword = true, cause = e)
                } catch (e: OoxmlDecryptionException) {
                    runCatching { cache.delete() }
                    Timber.w(e, "OOXML 복호화 실패")
                    throw DocumentError.Corrupted(e)
                }
                val result = try {
                    FileInputStream(cache).use { extract(it) }
                } catch (t: Throwable) {
                    runCatching { cache.delete() }
                    throw t
                }
                onCachedFile(cache)
                result
            }
        }
    }

    private fun createCacheFile(context: Context): File {
        val dir = File(context.cacheDir, UNLOCK_CACHE_DIR).apply { mkdirs() }
        // 오래된 잔여 캐시 정리
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        return File.createTempFile("unlocked-", ".ooxml", dir)
    }
}
