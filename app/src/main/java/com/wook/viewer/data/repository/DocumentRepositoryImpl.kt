package com.wook.viewer.data.repository

import android.net.Uri
import com.wook.viewer.data.local.dao.BookmarkDao
import com.wook.viewer.data.local.dao.RecentDocumentDao
import com.wook.viewer.data.local.entity.BookmarkEntity
import com.wook.viewer.data.local.entity.RecentDocumentEntity
import com.wook.viewer.data.saf.SafFileSource
import com.wook.viewer.domain.model.Bookmark
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.RecentDocument
import com.wook.viewer.domain.model.RecentSortOrder
import com.wook.viewer.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val saf: SafFileSource,
    private val dao: RecentDocumentDao,
    private val bookmarkDao: BookmarkDao
) : DocumentRepository {

    override suspend fun resolveDocument(uri: Uri): Document? {
        val doc = saf.resolve(uri)
        saf.persistPermission(uri)
        return doc
    }

    override fun observeRecent(order: RecentSortOrder, limit: Int): Flow<List<RecentDocument>> {
        val flow = when (order) {
            RecentSortOrder.RECENT -> dao.observeAllByRecent(limit)
            RecentSortOrder.NAME -> dao.observeAllByName(limit)
        }
        return flow.map { rows -> rows.map(::toDomain) }
    }

    override suspend fun addOrUpdateRecent(doc: Document, lastPageIndex: Int) {
        // upsert는 REPLACE 전략 — 기존 pinned 상태가 덮어써지므로 명시적으로 보존
        val existingPinned = dao.getPinned(doc.uri.toString()) ?: false
        dao.upsert(
            RecentDocumentEntity(
                uriString = doc.uri.toString(),
                displayName = doc.displayName,
                formatName = doc.format.name,
                lastOpenedAt = System.currentTimeMillis(),
                lastPageIndex = lastPageIndex,
                pinned = existingPinned
            )
        )
    }

    override suspend fun updateLastPage(uriString: String, pageIndex: Int) {
        dao.updateLastPage(uriString, pageIndex, System.currentTimeMillis())
    }

    override suspend fun setPinned(uriString: String, pinned: Boolean) {
        dao.setPinned(uriString, pinned)
    }

    override suspend fun removeRecent(uriString: String) {
        dao.delete(uriString)
        bookmarkDao.removeAll(uriString)
    }

    override suspend fun writeTextContent(uri: Uri, content: String, charset: String) {
        saf.writeText(uri, content, charset)
    }

    // ---- Bookmarks ----

    override fun observeBookmarks(uriString: String): Flow<List<Bookmark>> =
        bookmarkDao.observeForDocument(uriString).map { rows ->
            rows.map { Bookmark(it.uriString, it.pageIndex, it.createdAt, it.label) }
        }

    override suspend fun isBookmarked(uriString: String, pageIndex: Int): Boolean =
        bookmarkDao.exists(uriString, pageIndex)

    override suspend fun addBookmark(uriString: String, pageIndex: Int) {
        bookmarkDao.add(
            BookmarkEntity(
                uriString = uriString,
                pageIndex = pageIndex,
                createdAt = System.currentTimeMillis(),
                label = null
            )
        )
    }

    override suspend fun removeBookmark(uriString: String, pageIndex: Int) {
        bookmarkDao.remove(uriString, pageIndex)
    }

    override suspend fun toggleBookmark(uriString: String, pageIndex: Int): Boolean {
        val existed = bookmarkDao.exists(uriString, pageIndex)
        return if (existed) {
            bookmarkDao.remove(uriString, pageIndex)
            false
        } else {
            addBookmark(uriString, pageIndex)
            true
        }
    }

    private fun toDomain(e: RecentDocumentEntity) = RecentDocument(
        uriString = e.uriString,
        displayName = e.displayName,
        format = runCatching { DocumentFormat.valueOf(e.formatName) }.getOrDefault(DocumentFormat.PDF),
        lastOpenedAt = e.lastOpenedAt,
        lastPageIndex = e.lastPageIndex,
        pinned = e.pinned
    )
}
