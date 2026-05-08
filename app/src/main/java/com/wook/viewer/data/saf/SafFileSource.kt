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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF 진단 예외 — 어디서/왜 실패했는지 메시지에 담아 디버그 빌드에서 사용자에게 노출.
 */
class SafResolveException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * SAF(Storage Access Framework) URI를 다루는 게이트웨이.
 */
@Singleton
class SafFileSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun persistPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { Timber.w(it, "takePersistableUriPermission failed for $uri") }
    }

    /**
     * URI에 대응되는 Document를 만든다.
     *
     * 실패 시 [SafResolveException]을 던진다 (이전 버전은 null 반환). 메시지에는
     * 어느 단계에서 막혔는지(query / DocumentFile / 포맷 감지) 명시된다.
     */
    @Throws(SafResolveException::class)
    fun resolve(uri: Uri): Document {
        val mime = runCatching { resolver.getType(uri) }
            .onFailure { Timber.w(it, "getType failed for $uri") }
            .getOrNull()

        var displayName: String? = null
        var size: Long? = null

        // 1차: ContentResolver.query()로 메타데이터 조회
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val nameIdx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cur.isNull(nameIdx)) {
                        displayName = cur.getString(nameIdx)
                    }
                    val sizeIdx = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cur.isNull(sizeIdx)) {
                        size = cur.getLong(sizeIdx)
                    }
                }
            }
        }.onFailure { Timber.w(it, "query failed for $uri") }

        // 2차: DocumentFile 폴백
        if (displayName.isNullOrBlank()) {
            runCatching {
                val df = DocumentFile.fromSingleUri(context, uri)
                displayName = df?.name
            }.onFailure { Timber.w(it, "DocumentFile lookup failed for $uri") }
        }

        // 3차: URI lastPathSegment에서 파일명 추출 (확장자 있는 경우만)
        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { '.' in it }
        }

        val name = displayName?.takeIf { it.isNotBlank() } ?: throw SafResolveException(
            "파일 이름을 가져오지 못했습니다. uri=$uri, mime=$mime"
        )

        val format = DocumentFormat.fromMimeType(mime)
            ?: DocumentFormat.fromExtension(name)
            ?: throw SafResolveException(
                "지원하지 않는 형식. name=$name, mime=$mime"
            )

        Timber.d("resolved: name=$name, format=$format, mime=$mime, size=$size")

        return Document(
            uri = uri,
            displayName = name,
            format = format,
            sizeBytes = size,
            lastModified = null
        )
    }
}
