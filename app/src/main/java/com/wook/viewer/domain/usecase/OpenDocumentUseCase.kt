package com.wook.viewer.domain.usecase

import android.net.Uri
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.repository.DocumentRepository
import com.wook.viewer.domain.repository.RendererRegistry
import javax.inject.Inject

class OpenDocumentUseCase @Inject constructor(
    private val repo: DocumentRepository,
    private val registry: RendererRegistry
) {
    sealed interface Result {
        data class Ok(val document: Document) : Result
        data class Unsupported(val name: String) : Result
        /** SAF/메타데이터 조회 실패. 디버그 빌드는 cause 메시지를 노출. */
        data class NotFound(val cause: Throwable? = null) : Result
    }

    suspend operator fun invoke(uri: Uri): Result {
        val doc = try {
            repo.resolveDocument(uri)
        } catch (t: Throwable) {
            return Result.NotFound(t)
        } ?: return Result.NotFound()

        if (registry.rendererFor(doc.format) == null) {
            return Result.Unsupported(doc.displayName)
        }
        repo.addOrUpdateRecent(doc)
        return Result.Ok(doc)
    }
}
