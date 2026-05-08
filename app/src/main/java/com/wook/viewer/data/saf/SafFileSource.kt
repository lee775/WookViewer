package com.wook.viewer.data.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

class SafResolveException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * SAF(Storage Access Framework) URI를 다루는 게이트웨이.
 *
 * displayName 조회 + 포맷 감지를 다단계로 시도하여, 일부 ContentProvider가
 * OpenableColumns를 노출하지 않는 경우에도 동작한다 (예: 일부 카톡/메신저 fileprovider).
 *
 * displayName이 끝까지 못 잡히면 MIME/매직바이트로 포맷 감지 후 "document.<ext>"
 * 합성 이름을 부여한다.
 */
@Singleton
class SafFileSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun persistPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Timber.w(it, "takePersistableUriPermission failed for $uri") }
    }

    @Throws(SafResolveException::class)
    fun resolve(uri: Uri): Document {
        val mime = runCatching { resolver.getType(uri) }
            .onFailure { Timber.w(it, "getType failed for $uri") }
            .getOrNull()

        val displayName = lookupDisplayName(uri)
        val size = lookupSize(uri)

        // 포맷 감지: MIME → 확장자(이름) → 매직바이트 sniffing
        val format = DocumentFormat.fromMimeType(mime)
            ?: displayName?.let { DocumentFormat.fromExtension(it) }
            ?: sniffFormat(uri)

        if (format == null) {
            // sniff 실패 사유 진단 — openInputStream을 직접 호출해 어디서 막혔는지 확인
            val sniffDiag = diagnoseStream(uri)
            throw SafResolveException(
                "포맷 감지 실패. name=$displayName, mime=$mime, " +
                "scheme=${uri.scheme}, authority=${uri.authority}, " +
                "sniff=$sniffDiag"
            )
        }

        val finalName = displayName?.takeIf { it.isNotBlank() }
            ?: "document.${format.extensions.first()}"

        Timber.d("resolved: name=$finalName, format=$format, mime=$mime, size=$size, sniffed=${displayName == null}")

        return Document(
            uri = uri,
            displayName = finalName,
            format = format,
            sizeBytes = size,
            lastModified = null
        )
    }

    // ---- displayName 다단계 조회 ----

    private fun lookupDisplayName(uri: Uri): String? {
        // 1: 명시적 projection으로 query
        queryDisplayName(uri, projection = arrayOf(OpenableColumns.DISPLAY_NAME))?.let { return it }
        // 2: null projection (일부 provider는 default 컬럼이 다름)
        queryDisplayName(uri, projection = null)?.let { return it }
        // 3: DocumentFile.fromSingleUri
        runCatching { DocumentFile.fromSingleUri(context, uri)?.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        // 4: URI lastPathSegment에서 확장자 있는 것만
        uri.lastPathSegment?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() && '.' in it }
            ?.let { return it }
        return null
    }

    private fun queryDisplayName(uri: Uri, projection: Array<String>?): String? = runCatching {
        resolver.query(uri, projection, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && !cur.isNull(idx)) cur.getString(idx) else null
            } else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun lookupSize(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val idx = cur.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cur.isNull(idx)) cur.getLong(idx) else null
            } else null
        }
    }.getOrNull()

    // ---- 매직바이트 sniffing ----

    private fun sniffFormat(uri: Uri): DocumentFormat? = runCatching {
        val header = ByteArray(8)
        val read = resolver.openInputStream(uri)?.use { input ->
            input.read(header)
        } ?: return null

        if (read < 4) return null

        when {
            // %PDF
            header[0] == PDF_MAGIC[0] && header[1] == PDF_MAGIC[1] &&
            header[2] == PDF_MAGIC[2] && header[3] == PDF_MAGIC[3] ->
                DocumentFormat.PDF

            // OLE Compound (HWP 5.0): D0 CF 11 E0
            header[0] == OLE_MAGIC[0] && header[1] == OLE_MAGIC[1] &&
            header[2] == OLE_MAGIC[2] && header[3] == OLE_MAGIC[3] ->
                DocumentFormat.HWP

            // ZIP (PK\x03\x04 or PK\x05\x06): DOCX/PPTX/HWPX
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() ->
                inspectZipFormat(uri)

            else -> null
        }
    }.onFailure { Timber.w(it, "sniffFormat failed for $uri") }
        .getOrNull()

    private fun inspectZipFormat(uri: Uri): DocumentFormat? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val matched = when {
                        name == "word/document.xml" -> DocumentFormat.DOCX
                        name.startsWith("ppt/slides/slide") && name.endsWith(".xml") -> DocumentFormat.PPTX
                        name == "Contents/header.xml" || name == "Contents/section0.xml" -> DocumentFormat.HWP
                        else -> null
                    }
                    if (matched != null) return@use matched
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                null
            }
        }
    }.onFailure { Timber.w(it, "inspectZipFormat failed for $uri") }
        .getOrNull()

    /**
     * openInputStream을 시도하여 어디서 막혔는지 사람-읽을-수-있게 보고.
     * 진단 메시지에만 사용 — 운영에서는 호출하지 않음.
     */
    private fun diagnoseStream(uri: Uri): String = try {
        val input = resolver.openInputStream(uri)
        if (input == null) {
            "openInputStream returned null"
        } else {
            input.use {
                val header = ByteArray(8)
                val n = it.read(header)
                if (n <= 0) {
                    "stream opened but read returned $n (empty/closed)"
                } else {
                    val hex = header.take(n).joinToString(" ") { b -> "%02X".format(b) }
                    "read $n bytes, header=[$hex] (no PDF/OLE/ZIP magic match)"
                }
            }
        }
    } catch (t: Throwable) {
        "${t::class.java.simpleName}: ${t.message}"
    }

    private companion object {
        val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46)  // %PDF
        val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte())
    }
}
