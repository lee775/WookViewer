package com.wook.viewer.data.crypt

import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * OOXML (DOCX/PPTX/XLSX) 암호화 파일 처리.
 *
 * 일반 OOXML 은 ZIP 컨테이너(매직 50 4B 03 04) — 평문.
 * 암호화 OOXML 은 OLE Compound File Binary(매직 D0 CF 11 E0 A1 B1 1A E1) —
 * 내부에 EncryptionInfo 스트림 + EncryptedPackage 스트림 보유.
 *
 * Apache POI의 [Decryptor]로 비밀번호 검증 + AES 복호화 → 평문 ZIP 바이트 획득.
 */
object OoxmlDecryptor {

    private val CFB_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    enum class Detection { ZIP_OOXML, ENCRYPTED_OOXML, UNKNOWN }

    /**
     * 입력 스트림 첫 8바이트를 보고 ZIP/암호화/알 수 없음을 분류.
     * 호출 후 [InputStream]은 8바이트 소비되지 않은 채로 반환 (PushbackInputStream으로 감쌈).
     */
    fun detect(input: InputStream): Pair<Detection, InputStream> {
        val pb = PushbackInputStream(input, 8)
        val header = ByteArray(8)
        val read = pb.read(header)
        if (read > 0) pb.unread(header, 0, read)
        if (read < 4) return Detection.UNKNOWN to pb

        val detection = when {
            header.startsWith(CFB_MAGIC) -> Detection.ENCRYPTED_OOXML
            header.startsWith(ZIP_MAGIC) -> Detection.ZIP_OOXML
            else -> Detection.UNKNOWN
        }
        return detection to pb
    }

    /**
     * 암호화 OOXML 을 [password] 로 복호화해 [outFile] 에 평문 ZIP 으로 저장.
     *
     * @throws WrongPasswordException 비밀번호 불일치
     * @throws OoxmlDecryptionException 그 외 복호화 실패
     */
    fun decryptTo(input: InputStream, password: String, outFile: File) {
        val fs = try {
            POIFSFileSystem(input)
        } catch (t: Throwable) {
            throw OoxmlDecryptionException("CFB 컨테이너 읽기 실패", t)
        }
        try {
            val info = EncryptionInfo(fs)
            val decryptor = Decryptor.getInstance(info)
            val verified = try {
                decryptor.verifyPassword(password)
            } catch (t: Throwable) {
                throw OoxmlDecryptionException("비밀번호 검증 단계 실패 (지원되지 않는 암호 방식일 수 있음)", t)
            }
            if (!verified) throw WrongPasswordException()

            val plain = try {
                decryptor.getDataStream(fs)
            } catch (t: Throwable) {
                throw OoxmlDecryptionException("암호화된 페이로드 스트림 열기 실패", t)
            }

            plain.use { src ->
                outFile.outputStream().use { dst -> src.copyTo(dst) }
            }
            Timber.d("OOXML 복호화 완료: ${outFile.length()} bytes")
        } finally {
            runCatching { fs.close() }
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}

class WrongPasswordException : RuntimeException("OOXML password mismatch")
class OoxmlDecryptionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
