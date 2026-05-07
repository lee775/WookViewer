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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF(Storage Access Framework) URI를 다루는 게이트웨이.
 * - URI 권한 영속화
 * - 메타데이터(이름/크기/MIME) 조회
 * - DocumentFormat 추론
 */
@Singleton
class SafFileSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** 사용자가 선택한 URI에 대한 영속 권한을 잡는다 (재부팅 후에도 유효). */
    fun persistPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun resolve(uri: Uri): Document? {
        val mime = resolver.getType(uri)
        var displayName: String? = null
        var size: Long? = null
        var lastModified: Long? = null

        resolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val nameIdx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) displayName = cur.getString(nameIdx)
                val sizeIdx = cur.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cur.isNull(sizeIdx)) size = cur.getLong(sizeIdx)
            }
        }

        // DocumentFile 폴백 (마지막 수정 시각)
        if (displayName == null || lastModified == null) {
            val df = DocumentFile.fromSingleUri(context, uri)
            if (displayName == null) displayName = df?.name
            lastModified = df?.lastModified()
        }

        val name = displayName ?: return null
        val format = DocumentFormat.fromMimeType(mime)
            ?: DocumentFormat.fromExtension(name)
            ?: return null

        return Document(
            uri = uri,
            displayName = name,
            format = format,
            sizeBytes = size,
            lastModified = lastModified
        )
    }
}
