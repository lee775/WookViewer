package com.wook.viewer.domain.repository

import android.net.Uri
import com.wook.viewer.domain.model.Bookmark
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    /** SAF URI로부터 문서 메타데이터를 읽어 Document 모델로 만든다. */
    suspend fun resolveDocument(uri: Uri): Document?

    /** 최근 문서 목록 (최신순). */
    fun observeRecent(limit: Int = 50): Flow<List<RecentDocument>>

    suspend fun addOrUpdateRecent(doc: Document, lastPageIndex: Int = 0)

    suspend fun updateLastPage(uriString: String, pageIndex: Int)

    suspend fun removeRecent(uriString: String)

    // ---- Bookmarks ----

    fun observeBookmarks(uriString: String): Flow<List<Bookmark>>
    suspend fun isBookmarked(uriString: String, pageIndex: Int): Boolean
    suspend fun addBookmark(uriString: String, pageIndex: Int)
    suspend fun removeBookmark(uriString: String, pageIndex: Int)
    suspend fun toggleBookmark(uriString: String, pageIndex: Int): Boolean
}
