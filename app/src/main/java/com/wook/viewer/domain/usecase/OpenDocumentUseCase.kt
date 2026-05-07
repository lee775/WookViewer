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
        data object NotFound : Result
    }

    suspend operator fun invoke(uri: Uri): Result {
        val doc = repo.resolveDocument(uri) ?: return Result.NotFound
        if (registry.rendererFor(doc.format) == null) {
            return Result.Unsupported(doc.displayName)
        }
        repo.addOrUpdateRecent(doc)
        return Result.Ok(doc)
    }
}
