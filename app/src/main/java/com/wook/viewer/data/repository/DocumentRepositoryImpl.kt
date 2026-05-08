package com.wook.viewer.data.repository

import android.net.Uri
import com.wook.viewer.data.local.dao.RecentDocumentDao
import com.wook.viewer.data.local.entity.RecentDocumentEntity
import com.wook.viewer.data.saf.SafFileSource
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val saf: SafFileSource,
    private val dao: RecentDocumentDao
) : DocumentRepository {

    /**
     * @return 항상 non-null Document. 실패 시 [com.wook.viewer.data.saf.SafResolveException] 던짐.
     */
    override suspend fun resolveDocument(uri: Uri): Document? {
        // 인터페이스 호환성 위해 Document?로 두되, 내부적으로는 항상 throw 또는 non-null 반환
        val doc = saf.resolve(uri)
        saf.persistPermission(uri)
        return doc
    }

    override fun observeRecent(limit: Int): Flow<List<RecentDocument>> =
        dao.observeAll(limit).map { rows -> rows.map(::toDomain) }

    override suspend fun addOrUpdateRecent(doc: Document, lastPageIndex: Int) {
        dao.upsert(
            RecentDocumentEntity(
                uriString = doc.uri.toString(),
                displayName = doc.displayName,
                formatName = doc.format.name,
                lastOpenedAt = System.currentTimeMillis(),
                lastPageIndex = lastPageIndex
            )
        )
    }

    override suspend fun updateLastPage(uriString: String, pageIndex: Int) {
        dao.updateLastPage(uriString, pageIndex, System.currentTimeMillis())
    }

    override suspend fun removeRecent(uriString: String) {
        dao.delete(uriString)
    }

    private fun toDomain(e: RecentDocumentEntity) = RecentDocument(
        uriString = e.uriString,
        displayName = e.displayName,
        format = runCatching { DocumentFormat.valueOf(e.formatName) }.getOrDefault(DocumentFormat.PDF),
        lastOpenedAt = e.lastOpenedAt,
        lastPageIndex = e.lastPageIndex
    )
}
