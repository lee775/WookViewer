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

    override suspend fun resolveDocument(uri: Uri): Document? {
        val doc = saf.resolve(uri) ?: return null
        // 최근 목록에서 다시 열 때를 위해 권한을 잡아둔다.
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
